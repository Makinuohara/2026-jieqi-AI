# Protocol Decisions

## Authority

Teacher answers override the public-interface DOCX, which overrides the assignment
DOCX. The Unveil PDF is implementation guidance only where it does not conflict.

## Required decisions

- WebSocket JSON, UTF-8, default port 8887.
- Every text frame must be strictly smaller than 1024 bytes.
- Server time is authoritative; network turns expire after 65 seconds.
- Kings start visible; all other pieces start hidden.
- A hidden piece reveals only after its first legal movement or capture.
- Flip-in-place is forbidden.
- Reveal type is sampled by the server from the owner's remaining hidden pool.
- 80 consecutive half-moves without a capture is a draw.
- Long check and long chase must be continuous and trigger on the sixth occurrence.
- Pawn long chase is a draw; pawn long check still loses.
- Failure to answer check is allowed. The opponent may win by capturing the king.

## Hidden information

`gameStart.initialBoard` contains only public information. A captured hidden
piece's type is sent to the capturing side, while the captured side receives the
literal string `"NULL"`. Spectators must not receive the type.

The optional `capturedType` field is an additive extension. Existing clients that
ignore unknown fields remain compatible.

