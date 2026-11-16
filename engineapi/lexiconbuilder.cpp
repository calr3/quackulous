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

#include <algorithm>
#include <cctype>
#include <cstdint>
#include <cstdio>
#include <fstream>
#include <memory>
#include <numeric>

#include "lexiconbuilder.h"
#include "md5.h"

using namespace QuackleApi;

namespace
{
const int maximumWordLength = Quackle::FixedLengthString::maxSize - 1;

// A port of QuackleIO::DawgFactory without Qt. The output must stay byte-identical to it, so the algorithm is
// kept as is.
class DawgBuilder
{
public:
	DawgBuilder()
	{
		m_root.c = QUACKLE_BLANK_MARK;
		m_root.lastchild = true;
	}

	bool pushWord(const Quackle::LetterString &word) { return m_root.pushWord(word); }

	void generate()
	{
		const int bucketCount = 2000;
		std::vector<std::vector<int>> bucket(bucketCount);

		m_nodelist.clear();
		m_nodelist.push_back(&m_root);
		m_root.print(m_nodelist);
		m_nodelist[0]->letterSum();

		for (unsigned int i = 0; i < m_nodelist.size(); i++)
		{
			bucket[m_nodelist[i]->sum % bucketCount].push_back(i);
			m_nodelist[i]->pointer = 0;
			m_nodelist[i]->written = false;
			m_nodelist[i]->deleted = false;
			m_nodelist[i]->cloneof = nullptr;
		}

		for (int b = 0; b < bucketCount; b++)
		{
			if (bucket[b].empty())
				continue;
			for (auto it = bucket[b].begin(); it != bucket[b].end() - 1; it++)
			{
				if (m_nodelist[*it]->deleted)
					continue;
				for (auto jt = it + 1; jt != bucket[b].end(); jt++)
				{
					if (!m_nodelist[*jt]->deleted && m_nodelist[*it]->equals(*m_nodelist[*jt]))
					{
						m_nodelist[*jt]->deleted = true;
						m_nodelist[*jt]->cloneof = m_nodelist[*it];
					}
				}
			}
		}

		m_nodelist.clear();
		m_nodelist.push_back(&m_root);
		m_root.print(m_nodelist);
	}

	bool write(const std::string &filename, const Md5Digest &hash, int wordCount, const Quackle::AlphabetParameters &alphabet,
		std::string *error) const
	{
		std::ofstream out(filename.c_str(), std::ios::out | std::ios::binary);
		unsigned char bytes[7];
		bytes[0] = (wordCount & 0x00FF0000) >> 16;
		bytes[1] = (wordCount & 0x0000FF00) >> 8;
		bytes[2] = (wordCount & 0x000000FF);

		out.put(1);
		out.write((const char *)hash.data(), hash.size());
		out.write((char *)bytes, 3);
		out.put((char)alphabet.length());
		for (Quackle::Letter i = alphabet.firstLetter(); i <= alphabet.lastLetter(); i++)
			out << alphabet.letterParameter(i).text() << ' ';

		for (const Node *n : m_nodelist)
		{
			const unsigned int p = (unsigned int)(n->deleted ? n->cloneof->pointer : n->pointer);
			bytes[0] = (p & 0x00FF0000) >> 16;
			bytes[1] = (p & 0x0000FF00) >> 8;
			bytes[2] = (p & 0x000000FF);
			bytes[3] = n->c - QUACKLE_FIRST_LETTER;
			const unsigned int pb = n->playability;
			bytes[4] = (pb & 0x00FF0000) >> 16;
			bytes[5] = (pb & 0x0000FF00) >> 8;
			bytes[6] = (pb & 0x000000FF);
			if (n->lastchild)
				bytes[3] |= 64;
			if (n->insmallerdict)
				bytes[3] |= 128;
			out.write((char *)bytes, 7);
		}

		if (!out)
		{
			*error = "could not write " + filename;
			return false;
		}
		return true;
	}

private:
	struct Node
	{
		Quackle::Letter c = 0;
		bool insmallerdict = false;
		int playability = 0;
		std::vector<Node> children;
		int pointer = 0;
		bool lastchild = false;
		mutable bool sumexplored = false;
		mutable unsigned int sum = 0;
		bool deleted = false;
		Node *cloneof = nullptr;
		bool written = false;

		bool pushWord(const Quackle::LetterString &word)
		{
			bool added;
			if (word.length() == 0)
			{
				added = (playability == 0);
				playability = 1;
				insmallerdict = true;
			}
			else
			{
				const Quackle::Letter first = word[0];
				int index = -1;
				for (unsigned int i = 0; i < children.size(); i++)
				{
					if (children[i].c == first)
					{
						index = i;
						break;
					}
				}
				if (index == -1)
				{
					Node n;
					n.c = first;
					children.push_back(n);
					index = (int)children.size() - 1;
				}
				added = children[index].pushWord(word.substr(1, word.length() - 1));
			}
			sumexplored = false;
			deleted = false;
			written = false;
			return added;
		}

		void print(std::vector<Node *> &nodelist)
		{
			written = true;
			if (children.empty())
				return;

			pointer = deleted ? cloneof->pointer : (int)nodelist.size();
			if (!deleted)
			{
				for (unsigned int i = 0; i < children.size(); i++)
					nodelist.push_back(&children[i]);
				for (unsigned int i = 0; i < children.size(); i++)
				{
					if (!children[i].deleted)
						children[i].print(nodelist);
					else if (!children[i].cloneof->written)
						children[i].cloneof->print(nodelist);
				}
			}
			children.back().lastchild = true;
		}

		int letterSum() const
		{
			if (sumexplored)
				return sum;
			sumexplored = true;
			sum = 5381 * 33 + (int)c;
			for (unsigned int i = 0; i < children.size(); i++)
				sum = (sum << 5) + sum + children[i].letterSum();
			return sum;
		}

		bool equals(const Node &n) const
		{
			if (playability != n.playability || c != n.c || children.size() != n.children.size() || insmallerdict != n.insmallerdict
				|| sum != n.sum)
				return false;
			for (unsigned int i = 0; i < children.size(); i++)
				if (!children[i].equals(n.children[i]))
					return false;
			return true;
		}
	};

	Node m_root;
	std::vector<Node *> m_nodelist;
};

// Writes the same file as QuackleIO::GaddagFactory in a fraction of the memory: the gaddag strings live in one
// byte buffer, and the trie is a flat array linked by sibling indexes rather than nested vectors.
class GaddagBuilder
{
public:
	void pushWord(const Quackle::LetterString &word)
	{
		const unsigned int length = word.length();
		for (unsigned int i = 1; i <= length; i++)
		{
			m_offsets.push_back((uint32_t)m_bytes.size());
			for (int j = int(i) - 1; j >= 0; j--)
				m_bytes.push_back(word[j]);
			if (i < length)
			{
				m_bytes.push_back(separator);
				for (unsigned int j = i; j < length; j++)
					m_bytes.push_back(word[j]);
			}
		}
	}

	bool write(const std::string &filename, const Md5Digest &hash, const BuildProgress &progress, std::string *error)
	{
		const size_t count = m_offsets.size();
		m_offsets.push_back((uint32_t)m_bytes.size());

		std::vector<uint32_t> order(count);
		std::iota(order.begin(), order.end(), 0);
		std::sort(order.begin(), order.end(), [this](uint32_t a, uint32_t b) {
			return std::lexicographical_compare(m_bytes.begin() + m_offsets[a], m_bytes.begin() + m_offsets[a + 1],
				m_bytes.begin() + m_offsets[b], m_bytes.begin() + m_offsets[b + 1]);
		});
		if (progress && !progress(BuildStage::Gaddag, 0.3))
			return cancelled(error);

		std::vector<Node> nodes(1);
		nodes[0].letter = QUACKLE_NULL_MARK;
		std::vector<uint32_t> path(maximumWordLength + 2, 0);
		uint32_t previous = 0, previousLength = 0;
		bool first = true;
		for (size_t k = 0; k < count; ++k)
		{
			const uint32_t string = order[k];
			const unsigned char *letters = m_bytes.data() + m_offsets[string];
			const uint32_t length = m_offsets[string + 1] - m_offsets[string];

			uint32_t common = 0;
			if (!first)
			{
				const unsigned char *previousLetters = m_bytes.data() + m_offsets[previous];
				while (common < length && common < previousLength && letters[common] == previousLetters[common])
					++common;
			}

			for (uint32_t depth = common; depth < length; ++depth)
			{
				const uint32_t added = (uint32_t)nodes.size();
				nodes.push_back(Node());
				nodes[added].letter = letters[depth];
				// Input is sorted, so the parent's latest child, if any, is where the previous string went next.
				if (depth == common && !first && common < previousLength)
					nodes[path[depth + 1]].nextSibling = added;
				else
					nodes[path[depth]].firstChild = added;
				path[depth + 1] = added;
			}
			nodes[path[length]].terminal = true;

			previous = string;
			previousLength = length;
			first = false;
		}

		std::vector<uint32_t>().swap(order);
		std::vector<uint32_t>().swap(m_offsets);
		std::vector<unsigned char>().swap(m_bytes);
		if (progress && !progress(BuildStage::Gaddag, 0.6))
			return cancelled(error);

		std::vector<uint32_t> layout;
		layout.reserve(nodes.size());
		std::vector<uint32_t> pointer(nodes.size(), 0);
		layout.push_back(0);
		lay(0, nodes, layout, pointer);

		std::ofstream out(filename.c_str(), std::ios::out | std::ios::binary);
		out.put(1);
		out.write((const char *)hash.data(), hash.size());
		std::vector<char> buffer;
		buffer.reserve(1 << 20);
		for (size_t i = 0; i < layout.size(); ++i)
		{
			const Node &node = nodes[layout[i]];
			uint32_t p = pointer[layout[i]];
			if (p != 0)
				p -= (uint32_t)i;
			if (p > 0xFFFFFF)
			{
				*error = "the word list is too large for a lexicon file";
				return false;
			}
			unsigned char flags = node.letter == separator ? QUACKLE_NULL_MARK : node.letter;
			if (node.terminal)
				flags |= 64;
			if (layout[i] == 0 || node.nextSibling == 0)
				flags |= 128;
			buffer.push_back((char)((p >> 16) & 0xFF));
			buffer.push_back((char)((p >> 8) & 0xFF));
			buffer.push_back((char)(p & 0xFF));
			buffer.push_back((char)flags);
			if (buffer.size() >= (1 << 20))
			{
				out.write(buffer.data(), buffer.size());
				buffer.clear();
			}
		}
		out.write(buffer.data(), buffer.size());

		if (!out)
		{
			*error = "could not write " + filename;
			return false;
		}
		return true;
	}

private:
	static constexpr unsigned char separator = QUACKLE_FIRST_LETTER + QUACKLE_MAXIMUM_ALPHABET_SIZE;

	struct Node
	{
		uint32_t firstChild = 0;
		uint32_t nextSibling = 0;
		unsigned char letter = 0;
		bool terminal = false;
	};

	static void lay(uint32_t node, const std::vector<Node> &nodes, std::vector<uint32_t> &layout, std::vector<uint32_t> &pointer)
	{
		if (nodes[node].firstChild == 0)
			return;
		pointer[node] = (uint32_t)layout.size();
		for (uint32_t child = nodes[node].firstChild; child != 0; child = nodes[child].nextSibling)
			layout.push_back(child);
		for (uint32_t child = nodes[node].firstChild; child != 0; child = nodes[child].nextSibling)
			lay(child, nodes, layout, pointer);
	}

	static bool cancelled(std::string *error)
	{
		*error = "cancelled";
		return false;
	}

	std::vector<unsigned char> m_bytes;
	std::vector<uint32_t> m_offsets;
};

std::string hexOf(const Md5Digest &digest)
{
	static const char hex[] = "0123456789abcdef";
	std::string ret;
	for (unsigned char byte : digest)
	{
		ret.push_back(hex[byte >> 4]);
		ret.push_back(hex[byte & 0x0F]);
	}
	return ret;
}
} // namespace

bool QuackleApi::buildLexicon(const std::string &wordListFile, const Quackle::AlphabetParameters &alphabet, const std::string &dawgFile,
	const std::string &gaddagFile, const BuildProgress &progress, LexiconBuildReport *report, std::string *error)
{
	*report = LexiconBuildReport();

	std::ifstream in(wordListFile.c_str(), std::ios::in | std::ios::binary);
	if (!in.is_open())
	{
		*error = "could not open " + wordListFile;
		return false;
	}
	in.seekg(0, std::ios::end);
	const double fileSize = std::max<double>(1, (double)in.tellg());
	in.seekg(0, std::ios::beg);

	auto dawg = std::make_unique<DawgBuilder>();
	GaddagBuilder gaddag;
	Md5Digest hash = {};

	std::string line;
	for (long lineNumber = 1; std::getline(in, line); ++lineNumber)
	{
		if (progress && lineNumber % 20000 == 0 && !progress(BuildStage::Reading, (double)in.tellg() / fileSize))
		{
			*error = "cancelled";
			return false;
		}

		size_t start = 0;
		while (start < line.size() && std::isspace((unsigned char)line[start]))
			++start;
		size_t end = start;
		while (end < line.size() && !std::isspace((unsigned char)line[end]))
			++end;
		if (start == end || (unsigned char)line[start] < 'A')
			continue;

		std::string word = line.substr(start, end - start);
		for (char &c : word)
			if ((unsigned char)c < 128)
				c = (char)std::toupper((unsigned char)c);

		UVString leftover;
		const Quackle::LetterString encoded = alphabet.encode(word, &leftover);
		bool encodable = leftover.empty() && !encoded.empty() && (int)encoded.length() <= maximumWordLength;
		for (unsigned int i = 0; encodable && i < encoded.length(); ++i)
			encodable = encoded[i] >= QUACKLE_FIRST_LETTER && encoded[i] <= alphabet.lastLetter();
		if (!encodable)
		{
			++report->unencodable;
			if (report->unencodableExamples.size() < 10)
				report->unencodableExamples.push_back(word);
			continue;
		}

		if (!dawg->pushWord(encoded))
		{
			++report->duplicates;
			continue;
		}

		++report->words;
		const Md5Digest wordHash = md5((const unsigned char *)encoded.constData(), encoded.length());
		for (size_t i = 0; i < hash.size(); ++i)
			hash[i] ^= wordHash[i];
		gaddag.pushWord(encoded);
	}

	if (report->words == 0)
	{
		*error = "the word list has no words this alphabet can spell";
		return false;
	}
	report->hash = hexOf(hash);

	const std::string dawgPartial = dawgFile + ".partial";
	const std::string gaddagPartial = gaddagFile + ".partial";

	if (progress && !progress(BuildStage::Dawg, 0))
	{
		*error = "cancelled";
		return false;
	}
	dawg->generate();
	if (!dawg->write(dawgPartial, hash, report->words, alphabet, error))
	{
		std::remove(dawgPartial.c_str());
		return false;
	}
	dawg.reset();

	if (progress && !progress(BuildStage::Gaddag, 0))
	{
		std::remove(dawgPartial.c_str());
		*error = "cancelled";
		return false;
	}
	if (!gaddag.write(gaddagPartial, hash, progress, error))
	{
		std::remove(dawgPartial.c_str());
		std::remove(gaddagPartial.c_str());
		return false;
	}

	if (std::rename(dawgPartial.c_str(), dawgFile.c_str()) != 0 || std::rename(gaddagPartial.c_str(), gaddagFile.c_str()) != 0)
	{
		*error = "could not move the finished lexicon into place";
		return false;
	}
	if (progress)
		progress(BuildStage::Gaddag, 1);
	return true;
}
