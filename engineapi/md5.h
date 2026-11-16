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

#ifndef QUACKLE_ENGINEAPI_MD5_H
#define QUACKLE_ENGINEAPI_MD5_H

#include <array>
#include <cstddef>

namespace QuackleApi
{

typedef std::array<unsigned char, 16> Md5Digest;

// RFC 1321. Lexicon files identify their word list by XORing the MD5 of every word.
Md5Digest md5(const unsigned char *data, size_t length);

}

#endif
