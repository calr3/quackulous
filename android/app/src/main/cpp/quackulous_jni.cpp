/*
 *  Quackle -- Crossword game artificial intelligence and analysis tool
 *  Copyright (C) 2005-2026 Jason Katz-Brown, John O'Laughlin, and John Fultz.
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

// JNI bridge for org.quackulous.engine.NativeEngine. Results cross as JSON, which the Kotlin side parses.

#include <jni.h>

#include <cstdio>
#include <memory>
#include <sstream>
#include <string>
#include <vector>

#include "engine.h"

namespace
{
std::unique_ptr<QuackleApi::Engine> engine;

std::string fromJava(JNIEnv *env, jstring string)
{
	if (string == nullptr)
		return std::string();
	const char *chars = env->GetStringUTFChars(string, nullptr);
	std::string ret(chars);
	env->ReleaseStringUTFChars(string, chars);
	return ret;
}

// NewStringUTF wants modified UTF-8, which differs from UTF-8 only for NUL and characters outside the BMP;
// going through a byte array avoids that for any text.
jstring toJava(JNIEnv *env, const std::string &string)
{
	jbyteArray bytes = env->NewByteArray((jsize)string.size());
	env->SetByteArrayRegion(bytes, 0, (jsize)string.size(), (const jbyte *)string.data());
	jclass stringClass = env->FindClass("java/lang/String");
	jmethodID constructor = env->GetMethodID(stringClass, "<init>", "([BLjava/lang/String;)V");
	jstring charset = env->NewStringUTF("UTF-8");
	jstring ret = (jstring)env->NewObject(stringClass, constructor, bytes, charset);
	env->DeleteLocalRef(bytes);
	env->DeleteLocalRef(charset);
	env->DeleteLocalRef(stringClass);
	return ret;
}

std::string quoted(const std::string &text)
{
	std::string ret = "\"";
	for (const char c : text)
	{
		switch (c)
		{
		case '"':
			ret += "\\\"";
			break;
		case '\\':
			ret += "\\\\";
			break;
		case '\n':
			ret += "\\n";
			break;
		case '\t':
			ret += "\\t";
			break;
		default:
			if ((unsigned char)c < 0x20)
			{
				char escaped[8];
				snprintf(escaped, sizeof(escaped), "\\u%04x", c);
				ret += escaped;
			}
			else
				ret += c;
		}
	}
	return ret + "\"";
}

std::string errorJson(const std::string &error)
{
	return "{\"error\":" + quoted(error) + "}";
}

std::string suggestionsJson(const std::vector<QuackleApi::Suggestion> &suggestions)
{
	std::ostringstream out;
	out.precision(10);
	out << "[";
	for (size_t i = 0; i < suggestions.size(); ++i)
	{
		const QuackleApi::Suggestion &s = suggestions[i];
		const char *kind = s.kind == QuackleApi::Suggestion::Kind::Place ? "place"
			: s.kind == QuackleApi::Suggestion::Kind::Exchange           ? "exchange"
																		 : "pass";
		out << (i ? "," : "") << "{\"kind\":\"" << kind << "\",\"position\":" << quoted(s.position) << ",\"word\":" << quoted(s.word)
			<< ",\"leave\":" << quoted(s.leave) << ",\"score\":" << s.score << ",\"equity\":" << s.equity << ",\"win\":" << s.win
			<< ",\"iterations\":" << s.iterations << ",\"tiles\":[";
		for (size_t j = 0; j < s.tiles.size(); ++j)
			out << (j ? "," : "") << "{\"row\":" << s.tiles[j].row << ",\"column\":" << s.tiles[j].column
				<< ",\"text\":" << quoted(s.tiles[j].text) << ",\"placed\":" << (s.tiles[j].placed ? "true" : "false") << "}";
		out << "]}";
	}
	out << "]";
	return out.str();
}

struct BuildCallback
{
	JNIEnv *env;
	jobject listener;
	jmethodID method;
};
} // namespace

extern "C"
{

	JNIEXPORT jstring JNICALL Java_org_quackulous_engine_NativeEngine_nativeInit(JNIEnv *env, jclass, jstring dataDirectory)
	{
		engine.reset();
		engine.reset(new QuackleApi::Engine);
		std::string error;
		if (!engine->initialize(fromJava(env, dataDirectory), &error))
		{
			engine.reset();
			return toJava(env, errorJson(error));
		}

		std::ostringstream out;
		out << "{\"rackSize\":" << engine->rackSize() << ",\"tiles\":[";
		const auto &tiles = engine->tiles();
		for (size_t i = 0; i < tiles.size(); ++i)
			out << (i ? "," : "") << "{\"text\":" << quoted(tiles[i].text) << ",\"score\":" << tiles[i].score
				<< ",\"count\":" << tiles[i].count << ",\"vowel\":" << (tiles[i].vowel ? "true" : "false") << "}";
		const QuackleApi::BoardLayout layout = engine->boardLayout();
		out << "],\"board\":{\"width\":" << layout.width << ",\"height\":" << layout.height << ",\"startRow\":" << layout.startRow
			<< ",\"startColumn\":" << layout.startColumn << ",\"letterMultipliers\":[";
		for (size_t i = 0; i < layout.letterMultipliers.size(); ++i)
			out << (i ? "," : "") << layout.letterMultipliers[i];
		out << "],\"wordMultipliers\":[";
		for (size_t i = 0; i < layout.wordMultipliers.size(); ++i)
			out << (i ? "," : "") << layout.wordMultipliers[i];
		out << "]}}";
		return toJava(env, out.str());
	}

	JNIEXPORT jstring JNICALL Java_org_quackulous_engine_NativeEngine_nativeBuildLexicon(
		JNIEnv *env, jclass, jstring wordList, jstring name, jobject listener)
	{
		if (!engine)
			return toJava(env, errorJson("the engine isn't initialized"));

		BuildCallback callback = { env, listener, nullptr };
		if (listener != nullptr)
		{
			jclass listenerClass = env->GetObjectClass(listener);
			callback.method = env->GetMethodID(listenerClass, "onProgress", "(ID)Z");
			env->DeleteLocalRef(listenerClass);
		}
		QuackleApi::BuildProgress progress = [&callback](QuackleApi::BuildStage stage, double fraction) {
			if (callback.method == nullptr)
				return true;
			const bool keepGoing
				= callback.env->CallBooleanMethod(callback.listener, callback.method, (jint)stage, (jdouble)fraction) == JNI_TRUE;
			return keepGoing && !callback.env->ExceptionCheck();
		};

		QuackleApi::LexiconBuildReport report;
		std::string error;
		if (!engine->buildLexicon(fromJava(env, wordList), fromJava(env, name), progress, &report, &error))
			return toJava(env, errorJson(error));

		std::ostringstream out;
		out << "{\"words\":" << report.words << ",\"duplicates\":" << report.duplicates << ",\"unencodable\":" << report.unencodable
			<< ",\"hash\":" << quoted(report.hash) << ",\"unencodableExamples\":[";
		for (size_t i = 0; i < report.unencodableExamples.size(); ++i)
			out << (i ? "," : "") << quoted(report.unencodableExamples[i]);
		out << "]}";
		return toJava(env, out.str());
	}

	JNIEXPORT jboolean JNICALL Java_org_quackulous_engine_NativeEngine_nativeHasBuiltLexicon(JNIEnv *env, jclass, jstring name)
	{
		return engine && engine->hasBuiltLexicon(fromJava(env, name)) ? JNI_TRUE : JNI_FALSE;
	}

	JNIEXPORT jstring JNICALL Java_org_quackulous_engine_NativeEngine_nativeLoadLexicon(JNIEnv *env, jclass, jstring name)
	{
		if (!engine)
			return toJava(env, "the engine isn't initialized");
		std::string error;
		return engine->loadLexicon(fromJava(env, name), &error) ? nullptr : toJava(env, error);
	}

	JNIEXPORT jboolean JNICALL Java_org_quackulous_engine_NativeEngine_nativeIsWord(JNIEnv *env, jclass, jstring word)
	{
		return engine && engine->isWord(fromJava(env, word)) ? JNI_TRUE : JNI_FALSE;
	}

	JNIEXPORT jstring JNICALL Java_org_quackulous_engine_NativeEngine_nativeSetPosition(
		JNIEnv *env, jclass, jobjectArray cells, jstring rack, jint myScore, jint opponentScore)
	{
		if (!engine)
			return toJava(env, "the engine isn't initialized");
		std::vector<std::string> cellStrings;
		const jsize count = env->GetArrayLength(cells);
		for (jsize i = 0; i < count; ++i)
		{
			jstring cell = (jstring)env->GetObjectArrayElement(cells, i);
			cellStrings.push_back(fromJava(env, cell));
			env->DeleteLocalRef(cell);
		}
		std::string error;
		return engine->setPosition(cellStrings, fromJava(env, rack), myScore, opponentScore, &error) ? nullptr : toJava(env, error);
	}

	JNIEXPORT jintArray JNICALL Java_org_quackulous_engine_NativeEngine_nativeUnseenCounts(JNIEnv *env, jclass)
	{
		const std::vector<int> counts = engine ? engine->unseenCounts() : std::vector<int>();
		jintArray ret = env->NewIntArray((jsize)counts.size());
		std::vector<jint> values(counts.begin(), counts.end());
		env->SetIntArrayRegion(ret, 0, (jsize)values.size(), values.data());
		return ret;
	}

	JNIEXPORT jstring JNICALL Java_org_quackulous_engine_NativeEngine_nativeStaticMoves(JNIEnv *env, jclass, jint count)
	{
		return toJava(env, engine ? suggestionsJson(engine->staticMoves(count)) : "[]");
	}

	JNIEXPORT jstring JNICALL Java_org_quackulous_engine_NativeEngine_nativeSimulate(
		JNIEnv *env, jclass, jint candidates, jint iterations, jint plies, jobject listener)
	{
		if (!engine)
			return toJava(env, "[]");

		jmethodID method = nullptr;
		if (listener != nullptr)
		{
			jclass listenerClass = env->GetObjectClass(listener);
			method = env->GetMethodID(listenerClass, "onProgress", "(I)Z");
			env->DeleteLocalRef(listenerClass);
		}
		auto progress = [env, listener, method](int done) {
			if (method == nullptr)
				return true;
			const bool keepGoing = env->CallBooleanMethod(listener, method, (jint)done) == JNI_TRUE;
			return keepGoing && !env->ExceptionCheck();
		};
		return toJava(env, suggestionsJson(engine->simulate(candidates, iterations, plies, progress)));
	}

} // extern "C"
