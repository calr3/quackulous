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

// Command-line driver for the engine API, for testing it on a desktop.

#include <cstdio>
#include <cstdlib>
#include <fstream>
#include <iostream>
#include <string>
#include <vector>

#include "datamanager.h"
#include "engine.h"
#include "generator.h"
#include "lexiconparameters.h"

namespace
{
int usage()
{
	std::cerr << "usage: quackleapi_tool build <data-dir> <word-list> <name>\n"
				 "       quackleapi_tool dump <data-dir> <dawg-file> <output-word-list>\n"
				 "       quackleapi_tool analyze <data-dir> <name> <rack> [<board-file>] [--seed=N] [--sim=ITERATIONS]\n"
				 "A board file has one line per row, '.' for an empty square and lowercase for a blank.\n";
	return 2;
}

void print(const std::vector<QuackleApi::Suggestion> &suggestions)
{
	for (const auto &s : suggestions)
	{
		if (s.kind == QuackleApi::Suggestion::Kind::Place)
			std::cout << s.position << " " << s.word;
		else if (s.kind == QuackleApi::Suggestion::Kind::Exchange)
			std::cout << "-" << s.word;
		else
			std::cout << "pass";
		std::cout << " score " << s.score << " equity " << s.equity << " leave " << (s.leave.empty() ? "-" : s.leave);
		if (s.iterations > 0)
			std::cout << " win " << s.win << " over " << s.iterations;
		std::cout << "\n";
	}
}
} // namespace

int main(int argc, char **argv)
{
	if (argc < 5)
		return usage();
	const std::string command = argv[1];

	QuackleApi::Engine engine;
	std::string error;
	if (!engine.initialize(argv[2], &error))
	{
		std::cerr << error << "\n";
		return 1;
	}

	if (command == "build")
	{
		QuackleApi::LexiconBuildReport report;
		if (!engine.buildLexicon(argv[3], argv[4], nullptr, &report, &error))
		{
			std::cerr << error << "\n";
			return 1;
		}
		std::cout << "words " << report.words << " duplicates " << report.duplicates << " unencodable " << report.unencodable << " hash "
				  << report.hash << "\n";
		for (const auto &word : report.unencodableExamples)
			std::cout << "unencodable: " << word << "\n";
		return 0;
	}

	if (command == "dump")
	{
		QUACKLE_LEXICON_PARAMETERS->loadDawg(argv[3]);
		if (!QUACKLE_LEXICON_PARAMETERS->hasDawg())
			return 1;
		Quackle::Generator generator;
		const Quackle::WordList words = generator.anagramLetters(
			"", Quackle::Generator::AnagramRearrange | Quackle::Generator::AddAnyLetters | Quackle::Generator::ClearBlanknesses);
		std::ofstream out(argv[4]);
		for (const auto &word : words)
			out << QUACKLE_ALPHABET_PARAMETERS->userVisible(word) << "\n";
		return 0;
	}

	if (command != "analyze")
		return usage();

	if (!engine.loadLexicon(argv[3], &error))
	{
		std::cerr << error << "\n";
		return 1;
	}

	const QuackleApi::BoardLayout layout = engine.boardLayout();
	std::vector<std::string> cells(layout.width * layout.height);
	int simIterations = 0;
	for (int i = 5; i < argc; ++i)
	{
		const std::string argument = argv[i];
		if (argument.rfind("--seed=", 0) == 0)
		{
			QUACKLE_DATAMANAGER->seedRandomNumbers(std::stoul(argument.substr(7)));
			continue;
		}
		if (argument.rfind("--sim=", 0) == 0)
		{
			simIterations = std::stoi(argument.substr(6));
			continue;
		}
		std::ifstream board(argument.c_str());
		std::string line;
		for (int row = 0; row < layout.height && std::getline(board, line); ++row)
			for (int column = 0; column < layout.width && column < (int)line.size(); ++column)
				if (line[column] != '.')
					cells[row * layout.width + column] = std::string(1, line[column]);
	}

	if (!engine.setPosition(cells, argv[4], 0, 0, &error))
	{
		std::cerr << error << "\n";
		return 1;
	}

	if (simIterations > 0)
		print(engine.simulate(10, simIterations, 2, nullptr));
	else
		print(engine.staticMoves(10));
	return 0;
}
