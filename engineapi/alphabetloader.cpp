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

#include <cctype>
#include <fstream>
#include <sstream>
#include <vector>

#include "alphabetloader.h"

static_assert(sizeof(UVChar) == 1, "the engine API expects UTF-8 user-visible strings");

namespace
{
bool parseInt(const std::string &text, int *value)
{
	if (text.empty())
		return false;
	size_t used = 0;
	try
	{
		*value = std::stoi(text, &used);
	}
	catch (const std::exception &)
	{
		return false;
	}
	return used == text.size();
}

bool startsWithBlank(const std::string &text)
{
	static const char blank[] = "blank";
	if (text.size() < sizeof(blank) - 1)
		return false;
	for (size_t i = 0; i < sizeof(blank) - 1; ++i)
		if (std::tolower((unsigned char)text[i]) != blank[i])
			return false;
	return true;
}
} // namespace

bool QuackleApi::loadAlphabet(const std::string &filename, Quackle::AlphabetParameters *alphabet, std::string *error)
{
	std::ifstream file(filename.c_str());
	if (!file.is_open())
	{
		*error = "could not open alphabet " + filename;
		return false;
	}

	Quackle::Letter letter = QUACKLE_FIRST_LETTER;
	std::string line;
	int lineNumber = 0;
	while (std::getline(file, line))
	{
		++lineNumber;
		std::istringstream fields(line);
		std::vector<std::string> strings;
		for (std::string field; fields >> field;)
			strings.push_back(field);
		if (strings.empty() || strings.front()[0] == '#')
			continue;

		const bool isBlank = startsWithBlank(strings.front());
		int score = 0, count = 0, isVowel = 0;
		const bool ok = isBlank
			? strings.size() >= 3 && parseInt(strings[1], &score) && parseInt(strings[2], &count)
			: strings.size() >= 5 && parseInt(strings[2], &score) && parseInt(strings[3], &count) && parseInt(strings[4], &isVowel);
		if (!ok)
		{
			*error = filename + ":" + std::to_string(lineNumber) + ": malformed letter";
			return false;
		}

		if (isBlank)
		{
			alphabet->setCount(QUACKLE_BLANK_MARK, count);
			alphabet->setScore(QUACKLE_BLANK_MARK, score);
			continue;
		}

		if (letter >= QUACKLE_FIRST_LETTER + QUACKLE_MAXIMUM_ALPHABET_SIZE)
		{
			*error = filename + ": too many letters";
			return false;
		}
		alphabet->setLetterParameter(letter, Quackle::LetterParameter(letter, strings[0], strings[1], score, count, isVowel != 0));
		++letter;
	}

	if (letter == QUACKLE_FIRST_LETTER)
	{
		*error = filename + ": no letters";
		return false;
	}
	return true;
}
