/*
 * Copyright 2003-2023 The IdeaVim authors
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE.txt file or at
 * https://opensource.org/licenses/MIT.
 */
package com.maddyhome.idea.vim.ex.ranges

import com.maddyhome.idea.vim.api.VimCaret
import com.maddyhome.idea.vim.api.VimEditor
import com.maddyhome.idea.vim.api.injector
import com.maddyhome.idea.vim.ex.exExceptionMessage
import com.maddyhome.idea.vim.helper.Msg
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly

/**
 * Handles the set of range values entered as part of an Ex command.
 */
public class Range {
  // This property should be private, but is used in tests
  @TestOnly
  public val addresses: MutableList<Address> = mutableListOf()

  // TODO: This isn't a default line. It's used to set a default value for count, if RANGE_IS_COUNT is set
  // The only value used is 1, so maybe we don't need it, or RANGE_IS_COUNT?
  private var defaultLine = -1

  /** Adds a range to the list */
  public fun addAddresses(range: Array<Address>) {
    addresses.addAll(range)
  }

  /** Gets the number of ranges in the list */
  public fun size(): Int = addresses.size

  /**
   * Sets the default line to be used by this range if no range was actually given by the user. -1 is used to
   * mean the current line.
   *
   * @param line The line or -1 for current line
   */
  public fun setDefaultLine(line: Int) {
    defaultLine = line
  }

  /**
   * If a command expects a line, Vim uses the last line of any range passed to the command
   *
   * @param editor  The editor to get the line for
   * @param caret   The caret to use for current line, initial search line, etc. if required
   * @return The line number represented by the range
   */
  public fun getLine(editor: VimEditor, caret: VimCaret): Int {
    return getLineRange(editor, caret).endLine
  }

  /**
   * If a command expects a count, Vim uses the last line of the range passed to the command
   *
   * Note that the command may also have a count passed as an argument, which takes precedence over any range. This
   * function only returns the count from the range. It is up to the caller to decide which count to use.
   *
   * @param editor  The editor to get the count for
   * @param caret   The caret to use for current line, initial search line, etc. if required
   * @return The last line specified in the range, to be treated as a count (one-based)
   */
  public fun getCount(editor: VimEditor, caret: VimCaret): Int {
    return processRange(editor, caret).endLine1
  }

  /**
   * Gets the line range represented by this Ex range
   *
   * @param editor  The editor to get the range for
   * @param caret   The caret to use for current line, initial search line, etc. if required
   * @return The line range (zero-based)
   */
  public fun getLineRange(editor: VimEditor, caret: VimCaret): LineRange {
    return processRange(editor, caret)
  }

  private fun processRange(editor: VimEditor, caret: VimCaret): LineRange {
    // Start with the range being the current line
    var startLine1 = if (defaultLine == -1) caret.getBufferPosition().line + 1 else defaultLine
    var endLine1 = startLine1

    // Now process each range component, moving the cursor if appropriate
    var count = 0
    for (address in addresses) {
      startLine1 = endLine1
      endLine1 = address.getLine1(editor, caret)
      if (address.isMove) {
        caret.moveToOffset(injector.motion.moveCaretToLineWithSameColumn(editor, endLine1 - 1, caret))
      }

      ++count
    }

    // We can get a negative end line with a simple `:-10` go to line command. Vim treats this as an error
    if (endLine1 < 0) {
      throw exExceptionMessage(Msg.e_invrange) // E16: Invalid range
    }

    // If only one address is given, make the start and end the same
    if (count == 1) startLine1 = endLine1

    return LineRange(startLine1 - 1, endLine1 - 1)
  }

  @NonNls
  override fun toString(): String = "Ranges[addresses=$addresses]"

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Range) return false

    if (defaultLine != other.defaultLine) return false
    if (addresses != other.addresses) return false

    return true
  }

  override fun hashCode(): Int {
    var result = defaultLine
    result = 31 * result + defaultLine
    result = 31 * result + addresses.hashCode()
    return result
  }
}