-- Conjure client for Bridje (nREPL)
local nrepl = require("conjure.remote.nrepl")
local client = require("conjure.client")
local log = require("conjure.log")
local mapping = require("conjure.mapping")
local str = require("conjure.nfnl.string")

local M = {}

M["buf-suffix"] = ".brj"
M["comment-prefix"] = "; "

local function state_init()
  return { conn = nil }
end

local state = client["new-state"](state_init)

local function find_port()
  local dir = vim.fn.getcwd()
  while dir ~= "/" do
    local file = dir .. "/.nrepl-port"
    local f = io.open(file, "r")
    if f then
      local port = f:read("*n")
      f:close()
      if port then
        return port, file
      end
    end
    dir = vim.fn.fnamemodify(dir, ":h")
  end
  return nil
end

local function with_conn_or_warn(f, opts)
  local conn = state().conn
  if conn then
    return f(conn)
  else
    if not (opts and opts["silent?"]) then
      log.append({ "; No connection" })
    end
    if opts and opts["else"] then
      return opts["else"]()
    end
  end
end

local function connected()
  return state().conn ~= nil
end

local function send(msg, cb)
  return with_conn_or_warn(function(conn)
    return conn.send(msg, cb)
  end)
end

local function display_conn_status(status)
  with_conn_or_warn(function(conn)
    local suffix = ""
    if conn.port_file_path then
      suffix = ": " .. conn.port_file_path
    end
    log.append({ str.join({ "; ", conn.host, ":", conn.port, " (", status, ")", suffix }) }, { ["break?"] = true })
  end)
end

local function disconnect()
  with_conn_or_warn(function(conn)
    conn.destroy()
    display_conn_status("disconnected")
    state().conn = nil
  end)
end

local function assume_session(session_id)
  local conn = state().conn
  if conn then
    conn.session = session_id
    log.append({ "; Session: " .. session_id }, { ["break?"] = true })
  end
end

local function clone_session()
  send({ op = "clone", session = "no-session" }, function(msg)
    if msg["new-session"] then
      assume_session(msg["new-session"])
    end
  end)
end

local function connect(opts)
  opts = opts or {}

  if state().conn then
    disconnect()
  end

  local host = opts.host or "localhost"
  local port, port_file_path = opts.port, opts.port_file_path
  if not port then
    port, port_file_path = find_port()
  end

  if not port then
    log.append({ "; No nREPL port file found" }, { ["break?"] = true })
    return
  end

  local function on_failure(err)
    display_conn_status(err)
    disconnect()
  end

  local function on_success()
    display_conn_status("connected")
    clone_session()
  end

  local function on_error(err)
    if err then
      display_conn_status(err)
    else
      disconnect()
    end
  end

  local function on_message(msg)
  end

  local function side_effect_callback(msg)
    if msg.out then
      log.append({ msg.out })
    end
    if msg.err then
      log.append({ "; " .. msg.err })
    end
  end

  local function default_callback(msg)
  end

  local conn = nrepl.connect({
    host = host,
    port = port,
    ["on-failure"] = on_failure,
    ["on-success"] = on_success,
    ["on-error"] = on_error,
    ["on-message"] = on_message,
    ["side-effect-callback"] = side_effect_callback,
    ["default-callback"] = default_callback,
  })

  conn.port_file_path = port_file_path
  state().conn = conn
end

M.context = function()
  return vim.fn.fnamemodify(vim.api.nvim_buf_get_name(0), ":t")
end

M.connect = connect

M.disconnect = disconnect

M["eval-str"] = function(opts)
  with_conn_or_warn(function(conn)
    if not conn.session then
      log.append({ "; No session" })
      return
    end

    log.append({ "; eval: " .. opts.code })

    send({
      op = "eval",
      code = opts.code,
      session = conn.session,
    }, function(msg)
      if msg.value then
        log.append({ msg.value })
        if opts["on-result"] then
          opts["on-result"](msg.value)
        end
      end
      if msg.ex then
        log.append({ "; Exception: " .. msg.ex })
      end
    end)
  end)
end

M["eval-file"] = function(opts)
  M["eval-str"]({
    code = table.concat(vim.api.nvim_buf_get_lines(0, 0, -1, false), "\n"),
    ["on-result"] = opts["on-result"],
  })
end

M["on-load"] = function()
  if find_port() then
    connect()
  end
end

M["on-filetype"] = function()
  mapping.buf("BridjeConnect", "cc", connect, { desc = "Connect to nREPL" })
  mapping.buf("BridjeDisconnect", "cd", disconnect, { desc = "Disconnect" })
end

M["on-exit"] = function()
  if state().conn then
    state().conn.destroy()
  end
end

return M
