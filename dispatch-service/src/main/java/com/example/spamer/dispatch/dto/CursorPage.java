package com.example.spamer.dispatch.dto;

import java.util.List;

// Deliberately carries no total count - cursor feeds don't expose one.
public record CursorPage<T>(List<T> items, String nextCursor) {
}
