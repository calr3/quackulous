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
#include <fstream>
#include <map>
#include <sys/stat.h>

#include "alphabetparameters.h"
#include "bag.h"
#include "board.h"
#include "boardparameters.h"
#include "datamanager.h"
#include "game.h"
#include "gameparameters.h"
#include "generator.h"
#include "lexiconparameters.h"
#include "sim.h"
#include "strategyparameters.h"

#include "alphabetloader.h"
#include "engine.h"

using namespace QuackleApi;

namespace
{
const char alphabetName[] = "lexulous";

bool fileExists(const std::string &path)
{
	struct stat buf;
	return stat(path.c_str(), &buf) == 0;
}

std::string visible(const Quackle::LetterString &letters)
{
	return QUACKLE_ALPHABET_PARAMETERS->userVisible(letters);
}
} // namespace

Engine::Engine()
	: m_dataManager(new Quackle::DataManager)
{
}

Engine::~Engine()
{
	m_game.reset();
	m_dataManager.reset();
}

bool Engine::initialize(const std::string &dataDirectory, std::string *error)
{
	m_dataDirectory = dataDirectory;
	m_dataManager->setAppDataDirectory(dataDirectory);
	m_dataManager->setUserDataDirectory(dataDirectory);
	m_dataManager->setBackupLexicon("default_english");

	Quackle::AlphabetParameters *alphabet = new Quackle::AlphabetParameters;
	if (!loadAlphabet(dataDirectory + "/alphabets/" + alphabetName + ".quackle_alphabet", alphabet, error))
	{
		delete alphabet;
		return false;
	}
	alphabet->setAlphabetName(alphabetName);
	m_dataManager->setAlphabetParameters(alphabet);
	m_dataManager->setParameters(new Quackle::LexulousParameters);
	m_dataManager->setBoardParameters(new Quackle::LexulousBoard);

	m_tiles.clear();
	for (Quackle::Letter letter = alphabet->firstLetter(); letter <= alphabet->lastLetter(); ++letter)
	{
		const Quackle::LetterParameter &parameter = alphabet->letterParameter(letter);
		m_tiles.push_back({ parameter.text(), parameter.score(), parameter.count(), parameter.isVowel() });
	}
	m_tiles.push_back({ "?", alphabet->score(QUACKLE_BLANK_MARK), alphabet->count(QUACKLE_BLANK_MARK), false });
	return true;
}

const std::vector<TileInfo> &Engine::tiles() const
{
	return m_tiles;
}

BoardLayout Engine::boardLayout() const
{
	const Quackle::BoardParameters *board = QUACKLE_BOARD_PARAMETERS;
	BoardLayout layout;
	layout.width = board->width();
	layout.height = board->height();
	layout.startRow = board->startRow();
	layout.startColumn = board->startColumn();
	for (int row = 0; row < layout.height; ++row)
	{
		for (int column = 0; column < layout.width; ++column)
		{
			layout.letterMultipliers.push_back(board->letterMultiplier(row, column));
			layout.wordMultipliers.push_back(board->wordMultiplier(row, column));
		}
	}
	return layout;
}

int Engine::rackSize() const
{
	return QUACKLE_PARAMETERS->rackSize();
}

std::string Engine::lexiconFile(const std::string &name, const std::string &extension) const
{
	return m_dataDirectory + "/lexica/" + name + extension;
}

bool Engine::buildLexicon(
	const std::string &wordListFile, const std::string &name, const BuildProgress &progress, LexiconBuildReport *report, std::string *error)
{
	mkdir((m_dataDirectory + "/lexica").c_str(), 0755);
	return QuackleApi::buildLexicon(
		wordListFile, *QUACKLE_ALPHABET_PARAMETERS, lexiconFile(name, ".dawg"), lexiconFile(name, ".gaddag"), progress, report, error);
}

bool Engine::hasBuiltLexicon(const std::string &name) const
{
	return fileExists(lexiconFile(name, ".dawg")) && fileExists(lexiconFile(name, ".gaddag"));
}

bool Engine::loadLexicon(const std::string &name, std::string *error)
{
	m_game.reset();
	m_hasLexicon = false;

	Quackle::LexiconParameters *lexicon = QUACKLE_LEXICON_PARAMETERS;
	lexicon->unloadAll();
	lexicon->setLexiconName(name);
	lexicon->loadDawg(lexiconFile(name, ".dawg"));
	if (!lexicon->hasDawg())
	{
		*error = "could not load " + lexiconFile(name, ".dawg");
		return false;
	}
	lexicon->loadGaddag(lexiconFile(name, ".gaddag"));
	if (!lexicon->hasGaddag())
	{
		*error = "could not load " + lexiconFile(name, ".gaddag") + ", or it doesn't match the .dawg";
		return false;
	}

	QUACKLE_STRATEGY_PARAMETERS->initialize(name);
	m_hasLexicon = true;
	return true;
}

bool Engine::hasLexicon() const
{
	return m_hasLexicon;
}

bool Engine::isWord(const std::string &word) const
{
	if (!m_hasLexicon)
		return false;
	UVString leftover;
	const Quackle::LetterString encoded = QUACKLE_ALPHABET_PARAMETERS->encode(word, &leftover);
	if (!leftover.empty() || encoded.empty())
		return false;
	Quackle::Generator generator;
	return generator.isAcceptableWord(QUACKLE_ALPHABET_PARAMETERS->clearBlankness(encoded));
}

bool Engine::setPosition(const std::vector<std::string> &cells, const std::string &rack, int myScore, int opponentScore, std::string *error)
{
	m_game.reset();
	if (!m_hasLexicon)
	{
		*error = "no dictionary is loaded";
		return false;
	}

	const BoardLayout layout = boardLayout();
	if ((int)cells.size() != layout.width * layout.height)
	{
		*error = "the board needs " + std::to_string(layout.width * layout.height) + " squares";
		return false;
	}

	std::map<Quackle::Letter, int> used;
	auto countTiles = [&used](const Quackle::LetterString &letters) {
		for (const Quackle::Letter letter : letters)
			++used[QUACKLE_ALPHABET_PARAMETERS->isBlankLetter(letter) ? QUACKLE_BLANK_MARK : letter];
	};

	Quackle::Board board;
	board.prepareEmptyBoard();
	for (int row = 0; row < layout.height; ++row)
	{
		int runStart = -1;
		Quackle::LetterString run;
		for (int column = 0; column <= layout.width; ++column)
		{
			const std::string text = column < layout.width ? cells[row * layout.width + column] : std::string();
			if (!text.empty())
			{
				UVString leftover;
				const Quackle::LetterString letter = QUACKLE_ALPHABET_PARAMETERS->encode(text, &leftover);
				if (!leftover.empty() || letter.length() != 1 || letter[0] == QUACKLE_BLANK_MARK)
				{
					*error = "\"" + text + "\" isn't a tile";
					return false;
				}
				if (runStart < 0)
					runStart = column;
				run += letter[0];
			}
			else if (runStart >= 0)
			{
				countTiles(run);
				board.makeMove(Quackle::Move::createPlaceMove(row, runStart, true, run));
				runStart = -1;
				run.clear();
			}
		}
	}

	UVString leftover;
	const Quackle::LetterString rackLetters = QUACKLE_ALPHABET_PARAMETERS->encode(rack, &leftover);
	if (!leftover.empty())
	{
		*error = "\"" + leftover + "\" in the rack isn't a tile";
		return false;
	}
	for (const Quackle::Letter letter : rackLetters)
	{
		if (QUACKLE_ALPHABET_PARAMETERS->isBlankLetter(letter))
		{
			*error = "write a blank in the rack as ?";
			return false;
		}
	}
	if ((int)rackLetters.length() > rackSize())
	{
		*error = "a rack holds at most " + std::to_string(rackSize()) + " tiles";
		return false;
	}
	countTiles(rackLetters);

	for (const auto &it : used)
	{
		const int available = QUACKLE_ALPHABET_PARAMETERS->count(it.first);
		if (it.second > available)
		{
			const std::string text = it.first == QUACKLE_BLANK_MARK ? "blank" : visible(Quackle::LetterString(1, it.first));
			*error = "the board and rack use " + std::to_string(it.second) + " of " + text + ", but the set only has "
				+ std::to_string(available);
			return false;
		}
	}

	Quackle::PlayerList players;
	Quackle::Player me(MARK_UV("You"), Quackle::Player::HumanPlayerType, 0);
	me.setScore(myScore);
	Quackle::Player opponent(MARK_UV("Opponent"), Quackle::Player::HumanPlayerType, 1);
	opponent.setScore(opponentScore);
	players.push_back(me);
	players.push_back(opponent);

	m_game.reset(new Quackle::Game);
	m_game->setPlayers(players);
	m_game->addPosition();

	Quackle::GamePosition &position = m_game->currentPosition();
	position.setCurrentPlayerRack(Quackle::Rack(), false);
	position.setOppRack(Quackle::Rack(), false);
	position.setBoard(board);
	position.setBag(board.tilesNotOnBoard());
	position.setCurrentPlayerRack(Quackle::Rack(rackLetters), true);

	Quackle::Bag remaining(position.bag());
	Quackle::Rack opponentRack;
	remaining.refill(opponentRack);
	position.setOppRack(opponentRack, true);
	return true;
}

std::vector<int> Engine::unseenCounts() const
{
	std::vector<int> counts(m_tiles.size(), 0);
	if (!m_game)
	{
		for (size_t i = 0; i < m_tiles.size(); ++i)
			counts[i] = m_tiles[i].count;
		return counts;
	}

	const Quackle::Bag unseen = m_game->currentPosition().unseenBag();
	for (const Quackle::Letter letter : unseen.tiles())
	{
		if (letter == QUACKLE_BLANK_MARK)
			++counts.back();
		else
			++counts[letter - QUACKLE_FIRST_LETTER];
	}
	return counts;
}

Suggestion Engine::suggestionFor(const Quackle::Move &move) const
{
	const Quackle::GamePosition &position = m_game->currentPosition();
	Suggestion suggestion;
	suggestion.score = move.score;
	suggestion.equity = move.equity;
	suggestion.win = move.win;
	suggestion.leave = visible(Quackle::String::alphabetize((position.currentPlayer().rack() - move).tiles()));

	switch (move.action)
	{
	case Quackle::Move::Place:
	{
		suggestion.kind = Suggestion::Kind::Place;
		suggestion.position = move.positionString();
		const Quackle::LetterString &tiles = move.tiles();
		for (unsigned int i = 0; i < tiles.length(); ++i)
		{
			PlacedTile tile;
			tile.row = move.startrow + (move.horizontal ? 0 : i);
			tile.column = move.startcol + (move.horizontal ? i : 0);
			tile.placed = !Quackle::Move::isAlreadyOnBoard(tiles[i]);
			const Quackle::Letter letter = tile.placed ? tiles[i] : position.board().letter(tile.row, tile.column);
			const bool blank
				= tile.placed ? QUACKLE_ALPHABET_PARAMETERS->isBlankLetter(letter) : position.board().isBlank(tile.row, tile.column);
			tile.text = visible(Quackle::LetterString(1,
				blank && !QUACKLE_ALPHABET_PARAMETERS->isBlankLetter(letter) ? QUACKLE_ALPHABET_PARAMETERS->setBlankness(letter) : letter));
			suggestion.word += tile.text;
			suggestion.tiles.push_back(tile);
		}
		break;
	}
	case Quackle::Move::Exchange:
		suggestion.kind = Suggestion::Kind::Exchange;
		suggestion.word = visible(move.tiles());
		break;
	default:
		suggestion.kind = Suggestion::Kind::Pass;
		break;
	}
	return suggestion;
}

std::vector<Suggestion> Engine::staticMoves(int count)
{
	std::vector<Suggestion> suggestions;
	if (!m_game)
		return suggestions;

	Quackle::GamePosition &position = m_game->currentPosition();
	position.kibitz(count);
	for (const Quackle::Move &move : position.moves())
		suggestions.push_back(suggestionFor(move));
	return suggestions;
}

std::vector<Suggestion> Engine::simulate(int candidates, int iterations, int plies, const std::function<bool(int)> &progress)
{
	std::vector<Suggestion> suggestions;
	if (!m_game)
		return suggestions;

	Quackle::GamePosition &position = m_game->currentPosition();
	position.kibitz(candidates);

	Quackle::Simulator simulator;
	simulator.setPosition(position);
	simulator.setIncludedMoves(position.moves());
	simulator.resetNumbers();

	const int chunk = 10;
	for (int done = 0; done < iterations;)
	{
		const int step = std::min(chunk, iterations - done);
		simulator.simulate(plies, step);
		done += step;
		if (progress && !progress(done))
			break;
	}

	for (const Quackle::Move &move : simulator.moves(/* prune */ true, /* byWin */ true))
	{
		Suggestion suggestion = suggestionFor(move);
		suggestion.iterations = (int)simulator.simmedMoveForMove(move).wins.incorporatedValues();
		suggestions.push_back(suggestion);
	}
	return suggestions;
}
