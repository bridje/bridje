-- Conjure client for Bridje (stdio REPL)
local stdio = require("conjure.remote.stdio")
local config = require("conjure.config")
local client = require("conjure.client")
local log = require("conjure.log")
local mapping = require("conjure.mapping")

local bridje_root = vim.fn.expand("~/src/james/bridje")

config.merge({
  client = {
    bridje = {
      stdio = {
        command = "java -Dpolyglot.engine.WarnInterpreterOnly=false -jar " .. bridje_root .. "/repl/build/libs/bridje-repl.jar",
        prompt_pattern = "bridje=> ",
      }
    }
  }
})

local state = client["new-state"](function()
  return { repl = nil }
end)

local function get_config(key)
  return config["get-in"]({ "client", "bridje", "stdio", key })
end

local function with_repl_or_warn(f, opts)
  local repl = state().repl
  if repl then
    return f(repl, opts)
  else
    log.append({ "; No REPL running, use <localleader>cs to start" })
  end
end

local function format_msg(msg)
  if msg and msg ~= "" then
    return vim.trim(msg)
  end
  return nil
end

local function unbatch(msgs)
  local result = {}
  for _, msg in ipairs(msgs) do
    -- In batch mode, messages are tables with 'out' and 'done?' keys
    local text = msg.out or msg.err or ""
    local formatted = format_msg(text)
    if formatted then
      table.insert(result, formatted)
    end
  end
  return result
end

local M = {}

M["buf-suffix"] = ".brj"
M["comment-prefix"] = "; "

M.context = function()
  return vim.fn.fnamemodify(vim.api.nvim_buf_get_name(0), ":t")
end

M.start = function()
  if state().repl then
    log.append({ "; REPL already running" })
    return
  end

  local cmd = get_config("command")
  local prompt = get_config("prompt_pattern")

  log.append({ "; Starting Bridje REPL..." })

  state().repl = stdio.start({
    ["prompt-pattern"] = prompt,
    cmd = cmd,

    ["on-success"] = function()
      log.append({ "; Bridje REPL started" })
    end,

    ["on-error"] = function(err)
      log.append({ "; REPL error: " .. err })
    end,

    ["on-exit"] = function(code, signal)
      if code and code ~= 0 then
        log.append({ "; REPL exited with code " .. code })
      end
      state().repl = nil
    end,

    ["on-stray-output"] = function(msg)
      local text = msg.out or msg.err or ""
      if text ~= "" then
        log.append({ text })
      end
    end,
  })
end

M.stop = function()
  with_repl_or_warn(function(repl)
    repl.destroy()
    state().repl = nil
    log.append({ "; REPL stopped" })
  end)
end

M.interrupt = function()
  with_repl_or_warn(function(repl)
    log.append({ "; Interrupting..." })
    repl.send_signal(vim.loop.constants.SIGINT)
  end)
end

M["eval-str"] = function(opts)
  with_repl_or_warn(function(repl)
    repl.send(
      opts.code .. "\n",
      function(msgs)
        local results = unbatch(msgs)
        for _, result in ipairs(results) do
          log.append({ result })
          if opts["on-result"] then
            opts["on-result"](result)
          end
        end
      end,
      { ["batch?"] = true }
    )
  end, opts)
end

M["eval-file"] = function(opts)
  M["eval-str"]({
    code = table.concat(vim.api.nvim_buf_get_lines(0, 0, -1, false), "\n"),
    ["on-result"] = opts["on-result"],
  })
end

M["on-load"] = function()
  -- Don't auto-start; let user start with <localleader>cs or first eval will prompt
end

M["on-filetype"] = function()
  mapping.buf("BridjeStart", "cs", M.start, { desc = "Start REPL" })
  mapping.buf("BridjeStop", "cS", M.stop, { desc = "Stop REPL" })
  mapping.buf("BridjeInterrupt", "ci", M.interrupt, { desc = "Interrupt" })
end

M["on-exit"] = function()
  if state().repl then
    state().repl.destroy()
  end
end

return M
