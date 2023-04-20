/*
 * Copyright 2003-2023 The IdeaVim authors
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE.txt file or at
 * https://opensource.org/licenses/MIT.
 */

package com.maddyhome.idea.vim.api

import com.maddyhome.idea.vim.common.Direction
import com.maddyhome.idea.vim.common.TextRange
import com.maddyhome.idea.vim.diagnostic.vimLogger
import com.maddyhome.idea.vim.helper.CharacterHelper
import com.maddyhome.idea.vim.helper.CharacterHelper.charType
import org.jetbrains.annotations.Contract
import kotlin.math.abs
import kotlin.math.min

// todo all this methods should return Long since editor.fileSize is long
// todo same for TextRange and motions
public abstract class VimSearchHelperBase : VimSearchHelper {
  override fun findNextWord(editor: VimEditor, searchFrom: Int, count: Int, bigWord: Boolean): Long {
    return findNextWord(editor.text(), searchFrom.toLong(), editor.fileSize(), count, bigWord, false)
  }

  override fun findNextWordEnd(editor: VimEditor, caret: ImmutableVimCaret, count: Int, bigWord: Boolean): Int {
    val chars = editor.text()
    val pos = caret.offset.point
    val size = editor.fileSize().toInt()
    return findNextWordEnd(chars, pos, size, count, bigWord, false)
  }

  override fun findNextWordEnd(
    chars: CharSequence,
    pos: Int,
    size: Int,
    count: Int,
    bigWord: Boolean,
    spaceWords: Boolean,
  ): Int {
    return VimSearchHelperBase.findNextWordEnd(chars, pos, size, count, bigWord, spaceWords)
  }

  public companion object {
    private val logger = vimLogger<VimSearchHelperBase>()

    public fun findNextWord(
      chars: CharSequence,
      pos: Long,
      size: Long,
      count: Int,
      bigWord: Boolean,
      spaceWords: Boolean,
    ): Long {
      var _count = count
      val step = if (_count >= 0) 1 else -1
      _count = abs(_count)
      var res = pos
      for (i in 0 until _count) {
        res = findNextWordOne(chars, res, size, step, bigWord, spaceWords)
        if (res == pos || res == 0L || res == size - 1) {
          break
        }
      }
      return res
    }

    // TODO: 18.08.2022 Make private
    public fun findNextWordOne(
      chars: CharSequence,
      pos: Long,
      size: Long,
      step: Int,
      bigWord: Boolean,
      spaceWords: Boolean,
    ): Long {
      var found = false
      var _pos = if (pos < size) pos else min(size, (chars.length - 1).toLong())
      // For back searches, skip any current whitespace so we start at the end of a word
      if (step < 0 && _pos > 0) {
        if (charType(chars[_pos - 1], bigWord) === CharacterHelper.CharacterType.WHITESPACE && !spaceWords) {
          _pos = skipSpace(chars, _pos - 1, step, size) + 1
        }
        if (_pos > 0 && charType(chars[_pos], bigWord) !== charType(chars[_pos - 1], bigWord)) {
          _pos += step
        }
      }
      var res = _pos
      if (_pos < 0 || _pos >= size) {
        return _pos
      }
      var type = charType(chars[_pos], bigWord)
      if (type === CharacterHelper.CharacterType.WHITESPACE && step < 0 && _pos > 0 && !spaceWords) {
        type = charType(chars[_pos - 1], bigWord)
      }
      _pos += step
      while (_pos in 0 until size && !found) {
        val newType = charType(chars[_pos], bigWord)
        if (newType !== type) {
          if (newType === CharacterHelper.CharacterType.WHITESPACE && step >= 0 && !spaceWords) {
            _pos = skipSpace(chars, _pos, step, size)
            res = _pos
          } else if (step < 0) {
            res = _pos + 1
          } else {
            res = _pos
          }
          type = charType(chars[res], bigWord)
          found = true
        }
        _pos += step
      }
      if (found) {
        if (res < 0) { // (pos <= 0)
          res = 0
        } else if (res >= size) { // (pos >= size)
          res = size - 1
        }
      } else if (_pos <= 0) {
        res = 0
      } else if (_pos >= size) {
        res = size
      }
      return res
    }

    /**
     * Skip whitespace starting with the supplied position.
     *
     * An empty line is considered a whitespace break.
     */
    // TODO: 18.08.2022 Make private
    // todo refactor
    public fun skipSpace(chars: CharSequence, offset: Long, step: Int, size: Long): Long {
      var _offset = offset
      var prev = 0.toChar()
      while (_offset in 0 until size) {
        val c = chars[_offset.toInt()]
        if (c == '\n' && c == prev) break
        if (charType(c, false) !== CharacterHelper.CharacterType.WHITESPACE) break
        prev = c
        _offset += step
      }
      return if (_offset < size) _offset else size - 1
    }

    public operator fun CharSequence.get(index: Long): Char {
      return this[index.toInt()]
    }

    public fun findNextWordEndOne(
      chars: CharSequence,
      pos: Int,
      size: Int,
      step: Int,
      bigWord: Boolean,
      spaceWords: Boolean,
    ): Int {
      var pos = pos
      var found = false
      // For forward searches, skip any current whitespace so we start at the start of a word
      if (step > 0 && pos < size - 1) {
        if (charType(chars[pos + 1], bigWord) === CharacterHelper.CharacterType.WHITESPACE &&
          !spaceWords
        ) {
          pos = (skipSpace(chars, (pos + 1).toLong(), step, size.toLong()) - 1).toInt()
        }
        if (pos < size - 1 &&
          charType(chars[pos], bigWord) !==
          charType(chars[pos + 1], bigWord)
        ) {
          pos += step
        }
      }
      var res = pos
      if (pos < 0 || pos >= size) {
        return pos
      }
      var type = charType(chars[pos], bigWord)
      if (type === CharacterHelper.CharacterType.WHITESPACE && step >= 0 && pos < size - 1 && !spaceWords) {
        type = charType(chars[pos + 1], bigWord)
      }
      pos += step
      while (pos >= 0 && pos < size && !found) {
        val newType = charType(chars[pos], bigWord)
        if (newType !== type) {
          if (step >= 0) {
            res = pos - 1
          } else if (newType === CharacterHelper.CharacterType.WHITESPACE && step < 0 && !spaceWords) {
            pos = skipSpace(chars, pos.toLong(), step, size.toLong()).toInt()
            res = pos
          } else {
            res = pos
          }
          found = true
        }
        pos += step
      }
      if (found) {
        if (res < 0) {
          res = 0
        } else if (res >= size) {
          res = size - 1
        }
      } else if (pos == size) {
        res = size - 1
      }
      return res
    }
    public fun findNextWordEnd(
      chars: CharSequence,
      pos: Int,
      size: Int,
      count: Int,
      bigWord: Boolean,
      spaceWords: Boolean,
    ): Int {
      var count = count
      val step = if (count >= 0) 1 else -1
      count = abs(count)
      var res = pos
      for (i in 0 until count) {
        res = findNextWordEndOne(chars, res, size, step, bigWord, spaceWords)
        if (res == pos || res == 0 || res == size - 1) {
          break
        }
      }
      return res
    }
  }

  override fun findNextCamelStart(chars: CharSequence, startIndex: Int, count: Int): Int? {
    return findCamelStart(chars, startIndex, count, Direction.FORWARDS)
  }

  override fun findPreviousCamelStart(chars: CharSequence, endIndex: Int, count: Int): Int? {
    return findCamelStart(chars, endIndex - 1, count, Direction.BACKWARDS)
  }

  override fun findNextCamelEnd(chars: CharSequence, startIndex: Int, count: Int): Int? {
    return findCamelEnd(chars, startIndex, count, Direction.FORWARDS)
  }

  override fun findPreviousCamelEnd(chars: CharSequence, endIndex: Int, count: Int): Int? {
    return findCamelEnd(chars, endIndex - 1, count, Direction.BACKWARDS)
  }

  /**
   * [startIndex] is inclusive
   */
  private fun findCamelStart(chars: CharSequence, startIndex: Int, count: Int, direction: Direction): Int? {
    assert(count >= 1)
    var counter = 0
    var offset = startIndex
    while (counter < count) {
      val searchFrom = if (counter == 0) offset else offset + direction.toInt()
      offset = findCamelStart(chars, searchFrom, direction) ?: return null
      ++counter
    }
    return offset
  }

  /**
   * [startIndex] is inclusive
   */
  private fun findCamelEnd(chars: CharSequence, startIndex: Int, count: Int, direction: Direction): Int? {
    assert(count >= 1)
    var counter = 0
    var offset = startIndex
    while (counter < count) {
      val searchFrom = if (counter == 0) offset else offset + direction.toInt()
      offset = findCamelEnd(chars, searchFrom, direction) ?: return null
      ++counter
    }
    return offset
  }

  /**
   * [startIndex] is inclusive
   */
  private fun findCamelStart(chars: CharSequence, startIndex: Int, direction: Direction): Int? {
    var pos = startIndex
    val size = chars.length

    if (pos < 0 || pos >= size) {
      return null
    }

    while (pos in 0 until size) {
      if (chars[pos].isUpperCase()) {
        if ((pos == 0 || !chars[pos - 1].isUpperCase()) ||
          (pos == size - 1 || chars[pos + 1].isLowerCase())
        ) {
          return pos
        }
      } else if (chars[pos].isLowerCase()) {
        if (pos == 0 || !chars[pos - 1].isLetter()) {
          return pos
        }
      } else if (chars[pos].isDigit()) {
        if (pos == 0 || !chars[pos - 1].isDigit()) {
          return pos
        }
      }
      pos += direction.toInt()
    }
    return null
  }

  /**
   * [startIndex] is inclusive
   */
  private fun findCamelEnd(chars: CharSequence, startIndex: Int, direction: Direction): Int? {
    var pos = startIndex
    val size = chars.length

    if (pos < 0 || pos >= size) {
      return pos
    }

    while (pos in 0 until size) {
      if (chars[pos].isUpperCase()) {
        if (pos == size - 1 || !chars[pos + 1].isLetter() ||
          (chars[pos + 1].isUpperCase() && pos < size - 2 && chars[pos + 2].isLowerCase())
        ) {
          return pos
        }
      } else if (chars[pos].isLowerCase()) {
        if (pos == size - 1 || !chars[pos + 1].isLowerCase()) {
          return pos
        }
      } else if (chars[pos].isDigit()) {
        if (pos == size - 1 || !chars[pos + 1].isDigit()) {
          return pos
        }
      }
      pos += direction.toInt()
    }
    return null
  }

  override fun findBlockQuoteInLineRange(editor: VimEditor, caret: ImmutableVimCaret, quote: Char, isOuter: Boolean): TextRange? {
    var leftQuote: Int
    var rightQuote: Int

    val caretOffset = caret.offset.point
    val quoteAfterCaret: Int = editor.text().indexOfNext(quote, caretOffset, true) ?: return null
    val quoteBeforeCaret: Int? = editor.text().indexOfPrevious(quote, caretOffset, true)
    val quotesBeforeCaret: Int = editor.text().occurrencesBeforeOffset(quote, caretOffset, true)

    if (((caretOffset == quoteAfterCaret) && quotesBeforeCaret % 2 == 0) || quoteBeforeCaret == null) {
      leftQuote = quoteAfterCaret
      rightQuote = editor.text().indexOfNext(quote, leftQuote + 1, true) ?: return null
    } else {
      leftQuote = quoteBeforeCaret
      rightQuote = quoteAfterCaret
    }
    if (!isOuter) {
      leftQuote++
      rightQuote--
    }
    return TextRange(leftQuote, rightQuote + 1)
  }

  /**
   * @param endOffset         right search border, it is not included in search
   */
  private fun CharSequence.occurrencesBeforeOffset(char: Char, endOffset: Int, currentLineOnly: Boolean, searchEscaped: Boolean = false): Int {
    var counter = 0
    var i = endOffset
    while (i > 0 && this[i + Direction.BACKWARDS.toInt()] != '\n') {
      i = this.indexOfPrevious(char, i, currentLineOnly, searchEscaped) ?: break
      counter++
    }
    return counter
  }

  /**
   * @param char              char to look for
   * @param startIndex        index to start the search from, included in search
   * @param currentLineOnly   true if search should stop after reaching '\n'
   * @param searchEscaped     true if escaped chars should appear in search
   *
   * @return the closest to [startIndex] position of [char], or null if no [char] was found
   */
  private fun CharSequence.indexOfNext(char: Char, startIndex: Int, currentLineOnly: Boolean, searchEscaped: Boolean = false): Int? {
    return findCharacterPosition(this, char, startIndex, Direction.FORWARDS, currentLineOnly, searchEscaped)
  }

  /**
   * @param char              char to look for
   * @param endIndex          right search border, it is not included in search
   * @param currentLineOnly   true if search should stop after reaching '\n'
   * @param searchEscaped     true if escaped chars should appear in search
   *
   * @return the closest to [endIndex] position of [char], or null if no [char] was found
   */
  private fun CharSequence.indexOfPrevious(char: Char, endIndex: Int, currentLineOnly: Boolean, searchEscaped: Boolean = false): Int? {
    if (endIndex == 0 || (currentLineOnly && this[endIndex - 1] == '\n')) return null
    return findCharacterPosition(this, char, endIndex - 1, Direction.BACKWARDS, currentLineOnly, searchEscaped)
  }

  /**
   * Gets the closest char position in the given direction
   *
   * @param startIndex        index to start the search from (included in search)
   * @param char              character to look for
   * @param currentLineOnly   true if search should break after reaching '\n'
   * @param searchEscaped     true if escaped chars should be returned
   * @param direction         direction to search (forward/backward)
   *
   * @return index of the closest char found (null if nothing was found)
   */
  private fun findCharacterPosition(charSequence: CharSequence, char: Char, startIndex: Int, direction: Direction, currentLineOnly: Boolean, searchEscaped: Boolean): Int? {
    var pos = startIndex
    while (pos >= 0 && pos < charSequence.length && (!currentLineOnly || charSequence[pos] != '\n')) {
      if (charSequence[pos] == char && (pos == 0 || searchEscaped || isQuoteWithoutEscape(charSequence, pos, char))) {
        return pos
      }
      pos += direction.toInt()
    }
    return null
  }

  /**
   * Returns true if [quote] is at this [position] and it's not escaped (like \")
   */
  private fun isQuoteWithoutEscape(chars: CharSequence, position: Int, quote: Char): Boolean {
    var i = position
    if (chars[i] != quote) return false
    var backslashCounter = 0
    while (i-- > 0 && chars[i] == '\\') {
      backslashCounter++
    }
    return backslashCounter % 2 == 0
  }

  override fun findNextSentenceStart(
    editor: VimEditor,
    caret: ImmutableVimCaret,
    count: Int,
    countCurrent: Boolean,
    requireAll: Boolean,
  ): Int {
    var count = count
    val dir = if (count > 0) 1 else -1
    count = Math.abs(count)
    val total = count
    val chars: CharSequence = editor.text()
    val start: Int = caret.offset.point
    val max: Int = editor.fileSize().toInt()
    var res = start
    while (count > 0 && res >= 0 && res <= max - 1) {
      res = findSentenceStart(editor, chars, res, max, dir, countCurrent, count > 1)
      if (res == 0 || res == max - 1) {
        count--
        break
      }
      count--
    }
    if (res < 0 && (!requireAll || total == 1)) {
      res = if (dir > 0) max - 1 else 0
    } else if (count > 0 && total > 1 && !requireAll) {
      res = if (dir > 0) max - 1 else 0
    } else if (count > 0 && total > 1 && requireAll) {
      res = -count
    }
    return res
  }

  override fun findNextSentenceEnd(
    editor: VimEditor,
    caret: ImmutableVimCaret,
    count: Int,
    countCurrent: Boolean,
    requireAll: Boolean,
  ): Int {
    var count = count
    val dir = if (count > 0) 1 else -1
    count = Math.abs(count)
    val total = count
    val chars: CharSequence = editor.text()
    val start: Int = caret.offset.point
    val max: Int = editor.fileSize().toInt()
    var res = start
    while (count > 0 && res >= 0 && res <= max - 1) {
      res = findSentenceEnd(editor, chars, res, max, dir, countCurrent && count == total, count > 1)
      if (res == 0 || res == max - 1) {
        count--
        break
      }
      count--
    }
    if (res < 0 && (!requireAll || total == 1)) {
      res = if (dir > 0) max - 1 else 0
    } else if (count > 0 && total > 1 && !requireAll) {
      res = if (dir > 0) max - 1 else 0
    } else if (count > 0 && total > 1 && requireAll) {
      res = -count
    }
    return res
  }

  private fun findSentenceStart(
    editor: VimEditor,
    chars: CharSequence,
    start: Int,
    max: Int,
    dir: Int,
    countCurrent: Boolean,
    multiple: Boolean,
  ): Int {
    // Save off the next paragraph since a paragraph is a valid sentence.
    val lline: Int = editor.offsetToBufferPosition(start).line
    val np: Int = findNextParagraph(editor, lline, if (dir == 1) Direction.FORWARDS else Direction.BACKWARDS, false)
    var end: Int
    // start < max was added to avoid exception and it may be incorrect
    end = if (start < max && chars[start] == '\n' && !countCurrent) {
      findSentenceEnd(editor, chars, start, max, -1, false, multiple)
    } else {
      findSentenceEnd(editor, chars, start, max, -1, true, multiple)
    }
    if (end == start && countCurrent && chars[end] == '\n') {
      return end
    }
    val pos = end - 1
    if (end >= 0) {
      var offset = end + 1
      while (offset < max) {
        val ch = chars[offset]
        if (!Character.isWhitespace(ch)) {
          break
        }
        offset++
      }
      if (dir > 0) {
        if (offset == start && countCurrent) {
          return offset
        } else if (offset > start) {
          return offset
        }
      } else {
        if (offset == start && countCurrent) {
          return offset
        } else if (offset < start) {
          return offset
        }
      }
    }
    end = if (dir > 0) {
      findSentenceEnd(editor, chars, start, max, dir, true, multiple)
    } else {
      findSentenceEnd(editor, chars, pos, max, dir, countCurrent, multiple)
    }
    var res = end + 1
    if (end != -1 && (chars[end] != '\n' || !countCurrent)) {
      while (res < max) {
        val ch = chars[res]
        if (!Character.isWhitespace(ch)) {
          break
        }
        res++
      }
    }

    // Now let's see which to return, the sentence we found or the paragraph we found.
    // This mess returns which ever is closer to our starting point (and in the right direction).
    if (res >= 0 && np >= 0) {
      if (dir > 0) {
        if (np < res || res < start) {
          res = np
        }
      } else {
        if (np > res || res >= start && !countCurrent) {
          res = np
        }
      }
    } else if (res == -1 && np >= 0) {
      res = np
    }
    // else we found neither, res already -1
    return res
  }

  private fun findSentenceEnd(
    editor: VimEditor,
    chars: CharSequence,
    start: Int,
    max: Int,
    dir: Int,
    countCurrent: Boolean,
    multiple: Boolean,
  ): Int {
    if (dir > 0 && start >= editor.fileSize().toInt() - 1) {
      return -1
    } else if (dir < 0 && start <= 0) {
      return -1
    }

    // Save off the next paragraph since a paragraph is a valid sentence.
    val lline: Int = editor.offsetToBufferPosition(start).line
    var np: Int = findNextParagraph(editor, lline, if (dir == 1) Direction.FORWARDS else Direction.BACKWARDS, false)

    // Sections are also end-of-sentence markers. However, { and } in column 1 don't count.
    // Since our section implementation only supports these and form-feed chars, we'll just
    // check for form-feeds below.
    var res = -1
    var offset = start
    var found = false
    // Search forward looking for a candidate end-of-sentence character (., !, or ?)
    while (offset >= 0 && offset < max && !found) {
      var ch = chars[offset]
      if (".!?".indexOf(ch) >= 0) {
        val end = offset // Save where we found the punctuation.
        offset++
        // This can be followed by any number of ), ], ", or ' characters.
        while (offset < max) {
          ch = chars[offset]
          if (")]\"'".indexOf(ch) == -1) {
            break
          }
          offset++
        }

        // The next character must be whitespace for this to be a valid end-of-sentence.
        if (offset >= max || Character.isWhitespace(ch)) {
          // So we have found the end of the next sentence. Now let's see if we ended
          // where we started (or further) on a back search. This will happen if we happen
          // to start this whole search already on a sentence end.
          if (offset - 1 == start && !countCurrent) {
            // Skip back to the sentence end so we can search backward from there
            // for the real previous sentence.
            offset = end
          } else {
            // Yeah - we found the real end-of-sentence. Save it off.
            res = offset - 1
            found = true
          }
        } else {
          // Turned out not to be an end-of-sentence so move back to where we were.
          offset = end
        }
      } else if (ch == '\n') {
        val end = offset // Save where we found the newline.
        if (dir > 0) {
          offset++
          while (offset < max) {
            ch = chars[offset]
            if (ch != '\n') {
              offset--
              break
            }
            if (offset == np && (end - 1 != start || countCurrent)) {
              break
            }
            offset++
          }
          if (offset == np && (end - 1 != start || countCurrent)) {
            res = end - 1
            found = true
          } else if (offset > end) {
            res = offset
            np = res
            found = true
          } else if (offset == end) {
            if (offset > 0 && chars[offset - 1] == '\n' && countCurrent) {
              res = end
              np = res
              found = true
            }
          }
        } else {
          if (offset > 0) {
            offset--
            while (offset > 0) {
              ch = chars[offset]
              if (ch != '\n') {
                offset++
                break
              }
              offset--
            }
          }
          if (offset < end) {
            res = if (end == start && countCurrent) {
              end
            } else {
              offset - 1
            }
            found = true
          }
        }
        offset = end
      } else if (ch == '\u000C') {
        res = offset
        found = true
      }
      offset += dir
    }

    // Now let's see which to return, the sentence we found or the paragraph we found.
    // This mess returns which ever is closer to our starting point (and in the right direction).
    if (res >= 0 && np >= 0) {
      if (dir > 0) {
        if (np < res || res < start) {
          res = np
        }
      } else {
        if (np > res || res >= start && !countCurrent) {
          res = np
        }
      }
    }
    /*
    else if (res == -1 && np >= 0)
    {
        res = np;
    }
    */return res
  }

  private fun findSentenceRangeEnd(
    editor: VimEditor,
    chars: CharSequence,
    start: Int,
    max: Int,
    count: Int,
    isOuter: Boolean,
    oneway: Boolean,
  ): Int {
    var count = count
    val dir = if (count > 0) 1 else -1
    count = Math.abs(count)
    val total = count
    val toggle = !isOuter
    var findend = dir < 1
    // Even = start, odd = end
    var which: Int
    val eprev = findSentenceEnd(editor, chars, start, max, -1, true, false)
    val enext = findSentenceEnd(editor, chars, start, max, 1, true, false)
    val sprev = findSentenceStart(editor, chars, start, max, -1, true, false)
    val snext = findSentenceStart(editor, chars, start, max, 1, true, false)
    if (snext == eprev) // On blank line
    {
      if (dir < 0 && !oneway) {
        return start
      }
      which = 0
      if (oneway) {
        findend = dir > 0
      } else if (dir > 0 && start < max - 1 && !Character.isSpaceChar(chars[start + 1])) {
        findend = true
      }
    } else if (start == snext) // On sentence start
    {
      if (dir < 0 && !oneway) {
        return start
      }
      which = if (dir > 0) 1 else 0
      if (dir < 0 && oneway) {
        findend = false
      }
    } else if (start == enext) // On sentence end
    {
      if (dir > 0 && !oneway) {
        return start
      }
      which = 0
      if (dir > 0 && oneway) {
        findend = true
      }
    } else if (start >= sprev && start <= enext && enext < snext) // Middle of sentence
    {
      which = if (dir > 0) 1 else 0
    } else  // Between sentences
    {
      which = if (dir > 0) 0 else 1
      if (dir > 0) {
        if (oneway) {
          if (start < snext - 1) {
            findend = true
          } else if (start == snext - 1) {
            count++
          }
        } else {
          findend = true
        }
      } else {
        if (oneway) {
          if (start > eprev + 1) {
            findend = false
          } else if (start == eprev + 1) {
            count++
          }
        } else {
          findend = true
        }
      }
    }
    var res = start
    while (count > 0 && res >= 0 && res <= max - 1) {
      res = if (toggle && which % 2 == 1 || isOuter && findend) {
        findSentenceEnd(editor, chars, res, max, dir, false, total > 1)
      } else {
        findSentenceStart(editor, chars, res, max, dir, false, total > 1)
      }
      if (res == 0 || res == max - 1) {
        count--
        break
      }
      if (toggle) {
        if (which % 2 == 1 && dir < 0) {
          res++
        } else if (which % 2 == 0 && dir > 0) {
          res--
        }
      }
      which++
      count--
    }
    if (res < 0 || count > 0) {
      res = if (dir > 0) (if (max > 0) max - 1 else 0) else 0
    } else if (isOuter && (dir < 0 && findend || dir > 0 && !findend)) {
      if (res != 0 && res != max - 1) {
        res -= dir
      }
    }
    if (chars[res] == '\n' && res > 0 && chars[res - 1] != '\n') {
      res--
    }
    return res
  }

  @Contract("_, _, _, _ -> new")
  override fun findSentenceRange(
    editor: VimEditor,
    caret: ImmutableVimCaret,
    count: Int,
    isOuter: Boolean,
  ): TextRange {
    val chars: CharSequence = editor.text()
    if (chars.length == 0) return TextRange(0, 0)
    val max: Int = editor.fileSize().toInt()
    val offset: Int = caret.offset.point
    val ssel: Int = caret.selectionStart
    val esel: Int = caret.selectionEnd
    return if (Math.abs(esel - ssel) > 1) {
      val start: Int
      val end: Int
      // Forward selection
      if (offset == esel - 1) {
        start = ssel
        end = findSentenceRangeEnd(editor, chars, offset, max, count, isOuter, true)
        TextRange(start, end + 1)
      } else {
        end = esel - 1
        start = findSentenceRangeEnd(editor, chars, offset, max, -count, isOuter, true)
        TextRange(end, start + 1)
      }
    } else {
      val end = findSentenceRangeEnd(editor, chars, offset, max, count, isOuter, false)
      var space = isOuter
      if (Character.isSpaceChar(chars[end])) {
        space = false
      }
      val start = findSentenceRangeEnd(editor, chars, offset, max, -1, space, false)
      TextRange(start, end + 1)
    }
  }

  override fun findNextParagraph(editor: VimEditor, caret: ImmutableVimCaret, count: Int, allowBlanks: Boolean): Int? {
    val line: Int = findNextParagraphLine(editor, caret.getBufferPosition().line, count, allowBlanks) ?: return null
    val lineCount: Int = editor.nativeLineCount()
    return if (line == lineCount - 1) {
      if (count > 0) editor.fileSize().toInt() - 1 else 0
    } else {
      editor.getLineStartOffset(line)
    }
  }

  /**
   * Find next paragraph bound offset
   * @param editor target editor
   * @param startLine line to start the search from (included in search)
   * @param direction search direction
   * @param allowBlanks true if we consider lines with whitespaces as empty
   * @return next paragraph offset
   */
  private fun findNextParagraph(editor: VimEditor, startLine: Int, direction: Direction, allowBlanks: Boolean): Int {
    val line: Int? = findNextParagraphLine(editor, startLine, direction, allowBlanks)
    return if (line == null) {
      if (direction == Direction.FORWARDS) editor.fileSize().toInt() - 1 else 0
    } else {
      editor.getLineStartOffset(line)
    }
  }

  override fun findParagraphRange(editor: VimEditor, caret: ImmutableVimCaret, count: Int, isOuter: Boolean): TextRange? {
    val line: Int = caret.getBufferPosition().line

    if (logger.isDebug()) {
      logger.debug("Starting paragraph range search on line $line")
    }

    val rangeInfo = (if (isOuter) findOuterParagraphRange(editor, line, count) else findInnerParagraphRange(editor, line, count)) ?: return null
    val startLine: Int = rangeInfo.first
    val endLine: Int = rangeInfo.second

    if (logger.isDebug()) {
      logger.debug("final start line= $startLine")
      logger.debug("final end line= $endLine")
    }

    val start: Int = editor.getLineStartOffset(startLine)
    val end: Int = editor.getLineStartOffset(endLine)
    return TextRange(start, end + 1)
  }

  private fun findOuterParagraphRange(editor: VimEditor, line: Int, count: Int): Pair<Int, Int>? {
    var expandStart = false
    var expandEnd = false

    var startLine: Int = if (editor.isLineEmpty(line, true)) {
      line
    } else {
      findNextParagraphLine(editor, line, -1, true) ?: return null
    }

    var endLine: Int = findNextParagraphLine(editor, line, count, true) ?: return null

    if (editor.isLineEmpty(startLine, true) && editor.isLineEmpty(endLine, true)) {
      if (startLine == line) {
        endLine--
        expandStart = true
      } else {
        startLine++
        expandEnd = true
      }
    } else if (!editor.isLineEmpty(endLine, true) && !editor.isLineEmpty(startLine, true) && startLine > 0) {
      startLine--
      expandStart = true
    } else {
      expandStart = editor.isLineEmpty(startLine, true)
      expandEnd = editor.isLineEmpty(endLine, true)
    }
    if (expandStart) {
      startLine = if (editor.isLineEmpty(startLine, true)) findLastEmptyLine(editor, startLine, Direction.BACKWARDS) else startLine
    }
    if (expandEnd) {
      endLine = if (editor.isLineEmpty(endLine, true)) findLastEmptyLine(editor, endLine, Direction.FORWARDS) else endLine
    }
    return Pair(startLine, endLine)
  }

  private fun findInnerParagraphRange(editor: VimEditor, line: Int, count: Int): Pair<Int, Int>? {
    val lineCount: Int = editor.lineCount()

    var startLine = line
    var endLine: Int

    if (!editor.isLineEmpty(startLine, true)) {
      startLine = findNextParagraphLine(editor, line, -1, true) ?: return null
      if (editor.isLineEmpty(startLine, true)) {
        startLine++
      }
      endLine = line
    } else {
      endLine = line - 1
    }
    // todo someone please refactor this if you understand what is going on
    var which = if (editor.isLineEmpty(startLine, true)) 0 else 1
    for (i in 0 until count) {
      if (which % 2 == 1) {
        val nextParagraphLine = findNextParagraphLine(editor, endLine, Direction.FORWARDS, true)
        endLine = if (nextParagraphLine == null || nextParagraphLine == 0) {
          if (i == count - 1) {
            lineCount - 1
          } else {
            return null
          }
        } else {
          nextParagraphLine - 1
        }
      } else {
        endLine++
      }
      which++
    }
    startLine = if (editor.isLineEmpty(startLine, true)) findLastEmptyLine(editor, startLine, Direction.BACKWARDS) else startLine
    endLine = if (editor.isLineEmpty(endLine, true)) findLastEmptyLine(editor, endLine, Direction.FORWARDS) else endLine
    return Pair(startLine, endLine)
  }

  /**
   * If we have multiple consecutive empty lines in an editor, the method returns the first
   * or last empty line in the group of empty lines, depending on the specified direction
   */
  private fun findLastEmptyLine(editor: VimEditor, line: Int, direction: Direction): Int {
    if (!editor.isLineEmpty(line, true)) {
      logger.error("Method findLastEmptyLine was called for non-empty line")
      return line
    }
    return if (direction == Direction.BACKWARDS) {
      val previousNonEmptyLine = skipEmptyLines(editor, line, Direction.BACKWARDS, true)
      previousNonEmptyLine + 1
    } else {
      val nextNonEmptyLine = skipEmptyLines(editor, line, Direction.FORWARDS, true)
      nextNonEmptyLine - 1
    }
  }
  /**
   * Find next paragraph bound line
   * @param editor target editor
   * @param startLine line to start the search from (included in search)
   * @param count search for the count-th occurrence
   * @param allowBlanks true if we consider lines with whitespaces as empty
   * @return next paragraph bound line if there is any
   */
  private fun findNextParagraphLine(editor: VimEditor, startLine: Int, count: Int, allowBlanks: Boolean): Int? {
    var line: Int? = startLine
    val lineCount: Int = editor.lineCount()
    val direction = if (count > 0) Direction.FORWARDS else Direction.BACKWARDS

    var i = abs(count)
    while (i > 0 && line != null) {
      line = findNextParagraphLine(editor, line, direction, allowBlanks)
      i--
    }

    if (count != 0 && i == 0 && line == null) {
      line = if (direction == Direction.FORWARDS) lineCount - 1 else 0
    }

    return line
  }

  /**
   * Searches for the next paragraph boundary (empty line)
   * @param editor target editor
   * @param startLine line to start the search from (included in search)
   * @param direction search direction
   * @param allowBlanks true if we consider lines with whitespaces as empty
   * @return next empty line if there is any
   */
  private fun findNextParagraphLine(editor: VimEditor, startLine: Int, direction: Direction, allowBlanks: Boolean): Int? {
    var line = skipEmptyLines(editor, startLine, direction, allowBlanks)
    while (line in 0 until editor.nativeLineCount()) {
      if (editor.isLineEmpty(line, allowBlanks)) return line
      line += direction.toInt()
    }
    return null
  }

  /**
   * Searches for the next non-empty line in the given direction
   * @param editor target editor
   * @param startLine line to start the search from (included in search)
   * @param direction search direction
   * @param allowBlanks true if we consider lines with whitespaces as empty
   * @return next non-empty line if there is any.
   * If there is no such line, the [editor.nativeLineCount()] is returned for Forwards direction and -1 for Backwards
   */
  private fun skipEmptyLines(editor: VimEditor, startLine: Int, direction: Direction, allowBlanks: Boolean): Int {
    var i = startLine
    while (i in 0 until editor.nativeLineCount()) {
      if (!editor.isLineEmpty(i, allowBlanks)) break
      i += direction.toInt()
    }
    return i
  }
}
