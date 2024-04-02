/*
 * Copyright 2003-2023 The IdeaVim authors
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE.txt file or at
 * https://opensource.org/licenses/MIT.
 */

package com.maddyhome.idea.vim.vimscript.model.commands

import com.maddyhome.idea.vim.api.ExecutionContext
import com.maddyhome.idea.vim.api.VimCaret
import com.maddyhome.idea.vim.api.VimEditor
import com.maddyhome.idea.vim.api.injector
import com.maddyhome.idea.vim.command.CommandFlags
import com.maddyhome.idea.vim.command.OperatorArguments
import com.maddyhome.idea.vim.common.TextRange
import com.maddyhome.idea.vim.diagnostic.vimLogger
import com.maddyhome.idea.vim.ex.ExException
import com.maddyhome.idea.vim.ex.MissingArgumentException
import com.maddyhome.idea.vim.ex.MissingRangeException
import com.maddyhome.idea.vim.ex.NoArgumentAllowedException
import com.maddyhome.idea.vim.ex.NoRangeAllowedException
import com.maddyhome.idea.vim.ex.ranges.LineRange
import com.maddyhome.idea.vim.ex.ranges.Range
import com.maddyhome.idea.vim.helper.Msg
import com.maddyhome.idea.vim.helper.noneOfEnum
import com.maddyhome.idea.vim.helper.vimStateMachine
import com.maddyhome.idea.vim.vimscript.model.Executable
import com.maddyhome.idea.vim.vimscript.model.ExecutionResult
import com.maddyhome.idea.vim.vimscript.model.VimLContext
import java.util.*

public sealed class Command(public var commandRange: Range, public val commandArgument: String) : Executable {
  override lateinit var vimContext: VimLContext
  override lateinit var rangeInScript: TextRange

  public abstract val argFlags: CommandHandlerFlags
  protected open val optFlags: EnumSet<CommandFlags> = noneOfEnum()
  private val logger = vimLogger<Command>()

  public abstract class ForEachCaret(range: Range, argument: String = "") : Command(range, argument) {
    public abstract fun processCommand(
      editor: VimEditor,
      caret: VimCaret,
      context: ExecutionContext,
      operatorArguments: OperatorArguments,
    ): ExecutionResult
  }

  public abstract class SingleExecution(range: Range, argument: String = "") : Command(range, argument) {
    public abstract fun processCommand(
      editor: VimEditor,
      context: ExecutionContext,
      operatorArguments: OperatorArguments,
    ): ExecutionResult
  }

  @Throws(ExException::class)
  override fun execute(editor: VimEditor, context: ExecutionContext): ExecutionResult {
    checkRanges(editor)
    checkArgument(editor)
    if (editor.nativeCarets().any { it.hasSelection() } && Flag.SAVE_VISUAL !in argFlags.flags) {
      editor.removeSelection()
      editor.removeSecondaryCarets()
    }
    if (argFlags.access == Access.WRITABLE && !editor.isDocumentWritable()) {
      logger.info("Trying to modify readonly document")
      return ExecutionResult.Error
    }

    val operatorArguments = OperatorArguments(
      editor.vimStateMachine.isOperatorPending(editor.mode),
      0,
      editor.mode,
    )

    val runCommand = { runCommand(editor, context, operatorArguments) }
    return when (argFlags.access) {
      Access.WRITABLE -> injector.application.runWriteAction(runCommand)
      Access.READ_ONLY -> injector.application.runReadAction(runCommand)
      Access.SELF_SYNCHRONIZED -> runCommand.invoke()
    }
  }

  private fun runCommand(editor: VimEditor, context: ExecutionContext, operatorArguments: OperatorArguments): ExecutionResult {
    var result: ExecutionResult = ExecutionResult.Success
    when (this) {
      is ForEachCaret -> {
        editor.forEachNativeCaret(
          { caret ->
            if (result is ExecutionResult.Success) {
              result = processCommand(editor, caret, context, operatorArguments)
            }
          },
          true,
        )
      }
      is SingleExecution -> result = processCommand(editor, context, operatorArguments)
    }
    return result
  }

  private fun checkRanges(editor: VimEditor) {
    if (RangeFlag.RANGE_FORBIDDEN == argFlags.rangeFlag && commandRange.size() != 0) {
      injector.messages.showStatusBarMessage(editor, injector.messages.message(Msg.e_norange))
      throw NoRangeAllowedException()
    }

    if (RangeFlag.RANGE_REQUIRED == argFlags.rangeFlag && commandRange.size() == 0) {
      injector.messages.showStatusBarMessage(editor, injector.messages.message(Msg.e_rangereq))
      throw MissingRangeException()
    }

    if (RangeFlag.RANGE_IS_COUNT == argFlags.rangeFlag) {
      commandRange.setDefaultLine(1)
    }
  }

  private fun checkArgument(editor: VimEditor) {
    if (ArgumentFlag.ARGUMENT_FORBIDDEN == argFlags.argumentFlag && commandArgument.isNotBlank()) {
      injector.messages.showStatusBarMessage(editor, injector.messages.message(Msg.e_argforb))
      throw NoArgumentAllowedException()
    }

    if (ArgumentFlag.ARGUMENT_REQUIRED == argFlags.argumentFlag && commandArgument.isBlank()) {
      injector.messages.showStatusBarMessage(editor, injector.messages.message(Msg.e_argreq))
      throw MissingArgumentException()
    }
  }

  public enum class RangeFlag {
    /**
     * Indicates that a range must be specified with this command
     */
    RANGE_REQUIRED,

    /**
     * Indicates that a range is optional for this command
     */
    RANGE_OPTIONAL,

    /**
     * Indicates that a range can't be specified for this command
     */
    RANGE_FORBIDDEN,

    /**
     * Indicates that the command takes a count, not a range - effects default
     * Works like RANGE_OPTIONAL
     */
    RANGE_IS_COUNT,
  }

  public enum class ArgumentFlag {
    /**
     * Indicates that an argument must be specified with this command
     */
    ARGUMENT_REQUIRED,

    /**
     * Indicates that an argument is optional for this command
     */
    ARGUMENT_OPTIONAL,

    /**
     * Indicates that an argument can't be specified for this command
     */
    ARGUMENT_FORBIDDEN,
  }

  public enum class Access {
    /**
     * Indicates that this is a command that modifies the editor
     */
    WRITABLE,

    /**
     * Indicates that this command does not modify the editor
     */
    READ_ONLY,

    /**
     * Indicates that this command handles writability by itself
     */
    SELF_SYNCHRONIZED,
  }

  public enum class Flag {
    /**
     * This command should not exit visual mode.
     *
     * Vim exits visual mode before command execution, but in this case :action will work incorrect.
     *   With this flag visual mode will not be exited while command execution.
     */
    SAVE_VISUAL,
  }

  public data class CommandHandlerFlags(
    val rangeFlag: RangeFlag,
    val argumentFlag: ArgumentFlag,
    val access: Access,
    val flags: Set<Flag>,
  )

  public fun flags(rangeFlag: RangeFlag, argumentFlag: ArgumentFlag, access: Access, vararg flags: Flag): CommandHandlerFlags =
    CommandHandlerFlags(rangeFlag, argumentFlag, access, flags.toSet())

  public fun getLine(editor: VimEditor): Int = commandRange.getLine(editor)

  public fun getLine(editor: VimEditor, caret: VimCaret): Int = commandRange.getLine(editor, caret)

  public fun getCount(editor: VimEditor, defaultCount: Int, checkCount: Boolean): Int {
    val count = if (checkCount) countArgument else -1

    val res = commandRange.getCount(editor, count)
    return if (res == -1) defaultCount else res
  }

  public fun getCount(editor: VimEditor, caret: VimCaret, defaultCount: Int, checkCount: Boolean): Int {
    val count = commandRange.getCount(editor, caret, if (checkCount) countArgument else -1)
    return if (count == -1) defaultCount else count
  }

  public fun getLineRange(editor: VimEditor): LineRange = commandRange.getLineRange(editor, -1)

  public fun getLineRange(editor: VimEditor, caret: VimCaret, checkCount: Boolean = false): LineRange {
    return commandRange.getLineRange(editor, caret, if (checkCount) countArgument else -1)
  }

  public fun getTextRange(editor: VimEditor, checkCount: Boolean): TextRange {
    val count = if (checkCount) countArgument else -1
    return commandRange.getTextRange(editor, count)
  }

  public fun getTextRange(editor: VimEditor, caret: VimCaret, checkCount: Boolean): TextRange {
    return commandRange.getTextRange(editor, caret, if (checkCount) countArgument else -1)
  }

  private val countArgument: Int
    get() = commandArgument.toIntOrNull() ?: -1
}
