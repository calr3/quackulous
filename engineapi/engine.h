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

#ifndef QUACKLE_ENGINEAPI_ENGINE_H
#define QUACKLE_ENGINEAPI_ENGINE_H

#include <functional>
#include <memory>
#include <string>
#include <vector>

#include "lexiconbuilder.h"

namespace Quackle
{
class DataManager;
class Game;
class Move;
}

namespace QuackleApi
{

struct TileInfo
{
	std::string text;
	int score = 0;
	int count = 0;
	bool vowel = false;
};

struct BoardLayout
{
	int width = 0;
	int height = 0;
	int startRow = 0;
	int startColumn = 0;
	// Row-major, width * height entries.
	std::vector<int> letterMultipliers;
	std::vector<int> wordMultipliers;
};

struct PlacedTile
{
	int row = 0;
	int column = 0;
	// Uppercase for a tile, lowercase for a blank.
	std::string text;
	// False for a tile already on the board that the word plays through.
	bool placed = false;
};

struct Suggestion
{
	enum class Kind { Place, Exchange, Pass };

	Kind kind = Kind::Pass;
	// Quackle's notation for a placement, such as "8H" (across) or "H8" (down).
	std::string position;
	// The whole word for a placement; the tiles swapped for an exchange.
	std::string word;
	std::vector<PlacedTile> tiles;
	std::string leave;
	int score = 0;
	double equity = 0;
	// Win probability from 0 to 1, and how many games it rests on; simulation only.
	double win = 0;
	int iterations = 0;
};

// The Lexulous rules, board and tiles over libquackle, without Qt. Quackle keeps its state in one global data
// manager, so only one Engine may exist at a time, and calls must not overlap.
class Engine
{
public:
	Engine();
	~Engine();
	Engine(const Engine &) = delete;
	Engine &operator=(const Engine &) = delete;

	// dataDirectory is laid out like Quackle's data directory: alphabets/lexulous.quackle_alphabet, strategy/, and
	// lexica/ for built dictionaries.
	bool initialize(const std::string &dataDirectory, std::string *error);

	// The alphabet's letters in order, then the blank as "?".
	const std::vector<TileInfo> &tiles() const;
	BoardLayout boardLayout() const;
	int rackSize() const;

	// Writes lexica/<name>.dawg and lexica/<name>.gaddag from a word list. Doesn't load them.
	bool buildLexicon(const std::string &wordListFile, const std::string &name, const BuildProgress &progress, LexiconBuildReport *report,
		std::string *error);
	bool hasBuiltLexicon(const std::string &name) const;
	bool loadLexicon(const std::string &name, std::string *error);
	bool hasLexicon() const;
	bool isWord(const std::string &word) const;

	// cells holds width * height entries, row-major: "" for an empty square, a letter, or a lowercase letter for a
	// blank. The rack uses "?" for a blank. The opponent's rack is drawn at random from the unseen tiles.
	bool setPosition(const std::vector<std::string> &cells, const std::string &rack, int myScore, int opponentScore, std::string *error);
	// Tiles neither on the board nor on the rack, counted in tiles() order.
	std::vector<int> unseenCounts() const;

	// The best moves by score plus leave value.
	std::vector<Suggestion> staticMoves(int count);
	// Simulates the best `candidates` static moves `plies` turns ahead. progress gets the iterations done so far and
	// returns false to stop early with the results so far.
	std::vector<Suggestion> simulate(int candidates, int iterations, int plies, const std::function<bool(int)> &progress);

private:
	Suggestion suggestionFor(const Quackle::Move &move) const;
	std::string lexiconFile(const std::string &name, const std::string &extension) const;

	std::unique_ptr<Quackle::DataManager> m_dataManager;
	std::unique_ptr<Quackle::Game> m_game;
	std::string m_dataDirectory;
	std::vector<TileInfo> m_tiles;
	bool m_hasLexicon = false;
};

}

#endif
