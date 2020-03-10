package ru.job4j.parser;

import java.sql.*;
import java.util.List;

public class WriteToDB {

    public void writeToDB(String urlBase, String username, String password, List<Vacancy> vacancyList) {
        try (Connection con = DriverManager.getConnection(urlBase, username, password)) {
            initTableDB(con);
            vacanciesToDB(con, vacancyList);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void initTableDB(Connection con) {
        try (final Statement st = con.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS vacancies (id serial PRIMARY KEY, name varchar(400) NOT NULL UNIQUE, text text, link varchar(400))");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void vacanciesToDB(Connection con, List<Vacancy> vacancyList) throws SQLException {
        con.setAutoCommit(false);
        try (final PreparedStatement st = con.prepareStatement("INSERT INTO vacancies (name, text, link) VALUES (?, ?, ?)")) {
            for (Vacancy vacancy : vacancyList) {
                st.setString(1, vacancy.getName());
                st.setString(2, vacancy.getText());
                st.setString(3, vacancy.getLink());
                st.addBatch();
            }
            st.executeBatch();
            con.commit();
        } catch (SQLException ex) {
            ex.printStackTrace();
            if (con != null) {
                try {
                    con.rollback();
                } catch (SQLException exp) {
                    exp.printStackTrace();
                }
            }
        }
        con.setAutoCommit(true);
    }
}
