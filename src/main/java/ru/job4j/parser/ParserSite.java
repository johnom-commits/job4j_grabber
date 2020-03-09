package ru.job4j.parser;

import java.time.LocalDateTime;

public interface ParserSite {
    void pagesToParse(LocalDateTime limitDay);

    void writeToDB(String url, String username, String password);
}
