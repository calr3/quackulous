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

#ifndef QUACKLE_ENGINEAPI_LEXICONBUILDER_H
#define QUACKLE_ENGINEAPI_LEXICONBUILDER_H

#include <functional>
#include <string>
#include <vector>

#include "alphabetparameters.h"

namespace QuackleApi
{

struct LexiconBuildReport
{
	int words = 0;
	int duplicates = 0;
	// Words with characters outside the alphabet, or longer than a lexicon can hold; skipped.
	int unencodable = 0;
	std::vector<std::string> unencodableExamples;
	// Hex MD5 identity of the word list, as Quackle's lexica/copyrights.txt keys it.
	std::string hash;
};

enum class BuildStage { Reading, Dawg, Gaddag };

// Called with progress through each stage, from 0 to 1. Returning false cancels the build.
typedef std::function<bool(BuildStage stage, double fraction)> BuildProgress;

// Reads a UTF-8 word list and writes the DAWG and GADDAG files the engine loads, byte for byte as Quackle's
// own tools would from the same words in the same order. Each line's first whitespace-separated token is a
// word; lines starting with anything before 'A' (such as '#') are skipped, and words are uppercased.
bool buildLexicon(const std::string &wordListFile, const Quackle::AlphabetParameters &alphabet, const std::string &dawgFile,
	const std::string &gaddagFile, const BuildProgress &progress, LexiconBuildReport *report, std::string *error);

}

#endif
