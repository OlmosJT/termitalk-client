package io.olmosjt.client;

import java.util.List;

public record Channel(String id, String name, List<String> initialMessages) {
}
