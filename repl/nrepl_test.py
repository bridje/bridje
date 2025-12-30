#!/usr/bin/env python3
"""
Simple nREPL client for testing Bridje nREPL server.

Usage:
    python3 nrepl_test.py [port]

If port is not specified, reads from .nrepl-port file.
"""
import socket
import sys
import os

def bencode_string(s):
    """Encode a string in bencode format"""
    b = s.encode('utf-8')
    return f"{len(b)}:".encode('utf-8') + b

def bencode_dict(d):
    """Encode a dictionary in bencode format"""
    result = b"d"
    for key in sorted(d.keys()):
        result += bencode_string(key)
        value = d[key]
        if isinstance(value, str):
            result += bencode_string(value)
        elif isinstance(value, int):
            result += bencode_int(value)
        elif isinstance(value, list):
            result += bencode_list(value)
    result += b"e"
    return result

def bencode_int(n):
    """Encode an integer in bencode format"""
    return f"i{n}e".encode('utf-8')

def bencode_list(lst):
    """Encode a list in bencode format"""
    result = b"l"
    for item in lst:
        if isinstance(item, str):
            result += bencode_string(item)
    result += b"e"
    return result

def decode_bencode(data, start=0):
    """Decode bencode data"""
    if start >= len(data):
        return None, start
    
    ch = chr(data[start])
    
    if ch == 'i':
        # Integer
        end = data.index(b'e', start)
        num = int(data[start+1:end])
        return num, end + 1
    elif ch == 'l':
        # List
        result = []
        pos = start + 1
        while pos < len(data) and chr(data[pos]) != 'e':
            item, pos = decode_bencode(data, pos)
            result.append(item)
        return result, pos + 1
    elif ch == 'd':
        # Dictionary
        result = {}
        pos = start + 1
        while pos < len(data) and chr(data[pos]) != 'e':
            key, pos = decode_bencode(data, pos)
            value, pos = decode_bencode(data, pos)
            result[key] = value
        return result, pos + 1
    elif ch.isdigit():
        # String
        colon = data.index(b':', start)
        length = int(data[start:colon])
        string_start = colon + 1
        string_end = string_start + length
        return data[string_start:string_end].decode('utf-8'), string_end
    else:
        raise ValueError(f"Unknown bencode type: {ch}")

def read_nrepl_port():
    """Read port from .nrepl-port file"""
    if os.path.exists('.nrepl-port'):
        with open('.nrepl-port', 'r') as f:
            return int(f.read().strip())
    return None

def send_and_receive(sock, msg):
    """Send a message and receive response(s)"""
    encoded = bencode_dict(msg)
    sock.sendall(encoded)
    
    responses = []
    data = b""
    
    while True:
        chunk = sock.recv(4096)
        if not chunk:
            break
        data += chunk
        
        # Try to decode all complete messages
        pos = 0
        while pos < len(data):
            try:
                response, new_pos = decode_bencode(data, pos)
                responses.append(response)
                pos = new_pos
                
                # Check if we got a "done" status
                if isinstance(response, dict) and "status" in response:
                    status = response["status"]
                    if isinstance(status, list) and "done" in status:
                        return responses
            except:
                break
        
        data = data[pos:]
    
    return responses

def main():
    # Determine port
    if len(sys.argv) > 1:
        port = int(sys.argv[1])
    else:
        port = read_nrepl_port()
        if port is None:
            print("Error: No port specified and .nrepl-port file not found")
            sys.exit(1)
    
    print(f"Connecting to localhost:{port}...")
    
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.connect(('localhost', port))
        sock.settimeout(5.0)
        
        # Test 1: describe
        print("\n=== Describe Server ===")
        responses = send_and_receive(sock, {"op": "describe", "id": "1"})
        for r in responses:
            print(r)
        
        # Test 2: clone (create session)
        print("\n=== Create Session ===")
        responses = send_and_receive(sock, {"op": "clone", "id": "2"})
        session_id = None
        for r in responses:
            print(r)
            if "new-session" in r:
                session_id = r["new-session"]
        
        if not session_id:
            print("Error: Failed to create session")
            return
        
        print(f"\nSession ID: {session_id}")
        
        # Test 3: eval
        print("\n=== Evaluate Code ===")
        code = "(+ 1 2 3)"
        print(f"Code: {code}")
        responses = send_and_receive(sock, {
            "op": "eval",
            "code": code,
            "session": session_id,
            "id": "3"
        })
        for r in responses:
            print(r)
            if "value" in r:
                print(f"Result: {r['value']}")
        
        # Test 4: ls-sessions
        print("\n=== List Sessions ===")
        responses = send_and_receive(sock, {"op": "ls-sessions", "id": "4"})
        for r in responses:
            print(r)
        
        # Test 5: close
        print("\n=== Close Session ===")
        responses = send_and_receive(sock, {
            "op": "close",
            "session": session_id,
            "id": "5"
        })
        for r in responses:
            print(r)
        
        sock.close()
        print("\n✓ All tests completed successfully!")
        
    except Exception as e:
        print(f"Error: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)

if __name__ == "__main__":
    main()
