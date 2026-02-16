/** Clasa pentru gestionarea spectacolelor
 * @author Sova Ioan-Rares
 * @version 27 Decembrie 2025
 */
package aplicatie_standup.app_standup.controller;

import aplicatie_standup.app_standup.model.Session;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/")
public class SpectacoleController {

    @Value("${spring.datasource.url}") private String dbUrl;
    @Value("${spring.datasource.username}") private String dbUser;
    @Value("${spring.datasource.password}") private String dbPass;

    private Connection getDbConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl, dbUser, dbPass);
    }

    private Session getSession(HttpSession httpSession) {
        return (Session) httpSession.getAttribute("currentSession");
    }

    // --- LOGIN & NAVIGARE ---

    @GetMapping({"/", "/index.html"})
    public String index(HttpSession httpSession) {
        if (getSession(httpSession) != null) {
            return "redirect:/spectacole";
        }
        return "index";
    }

    @PostMapping("/login")
    public String login(@RequestParam String username, @RequestParam String password,
                        HttpSession httpSession, Model model) {
        if (username.trim().isEmpty() || password.trim().isEmpty()) {
            model.addAttribute("error", "Completează username și parolă.");
            return "index";
        }
        try (Connection conn = getDbConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT Rol, ID_Spectator FROM Utilizator WHERE Username=? AND Parola=?")) {
            ps.setString(1, username);
            ps.setString(2, password);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String role = rs.getString("Rol");
                    Integer idSpectator = rs.getInt("ID_Spectator");
                    if (rs.wasNull()) idSpectator = null;
                    Session ses = new Session(username, role, idSpectator);
                    httpSession.setAttribute("currentSession", ses);
                    return "redirect:/spectacole";
                } else {
                    model.addAttribute("error", "Username sau parolă incorecte.");
                    return "index";
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            model.addAttribute("error", "Eroare DB la login: " + ex.getMessage());
            return "index";
        }
    }

    // --- METODA DE ÎNREGISTRARE  ---
    @PostMapping("/register")
    public String register(@RequestParam String regUsername,
                           @RequestParam String regPassword,
                           @RequestParam String regNume,
                           @RequestParam String regEmail,
                           @RequestParam String regTelefon,
                           Model model) {

        //  Validări de bază (câmpuri goale)
        if (regUsername.trim().isEmpty() || regPassword.trim().isEmpty() ||
                regNume.trim().isEmpty() || regEmail.trim().isEmpty() || regTelefon.trim().isEmpty()) {
            model.addAttribute("error", "Toate câmpurile sunt obligatorii!");
            return "index";
        }

        // Validare lungime parolă
        if (regPassword.length() < 5) {
            model.addAttribute("error", "Parola trebuie să aibă minim 5 caractere!");
            return "index";
        }

        // Validare format email
        if (!regEmail.contains("@gmail.com")) {
            model.addAttribute("error", "Email-ul trebuie să fie de tip @gmail.com!");
            return "index";
        }

        //  Validare Telefon (Regex: doar cifre, exact 10 caractere)
        if (!regTelefon.matches("\\d{10}")) {
            model.addAttribute("error", "Numărul de telefon trebuie să conțină exact 10 cifre și să nu aibă litere!");
            return "index";
        }

        try (Connection conn = getDbConnection()) {
            // Verificăm dacă USERNAME-ul există deja
            try (PreparedStatement check = conn.prepareStatement("SELECT COUNT(*) FROM Utilizator WHERE Username = ?")) {
                check.setString(1, regUsername);
                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        model.addAttribute("error", "Acest username este deja folosit!");
                        return "index";
                    }
                }
            }

            //  Verificăm dacă EMAIL sau TELEFON există deja în tabelul Spectator
            try (PreparedStatement checkSpec = conn.prepareStatement("SELECT COUNT(*) FROM Spectator WHERE Email_Spectator = ? OR Telefon_Spectator = ?")) {
                checkSpec.setString(1, regEmail);
                checkSpec.setString(2, regTelefon);
                try (ResultSet rs = checkSpec.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        model.addAttribute("error", "Acest email sau telefon există deja în baza de date!");
                        return "index";
                    }
                }
            }

            // Inserăm în SPECTATOR (Nume, Email, Telefon) -> Obținem ID
            int idSpectatorNou = 0;
            String sqlSpectator = "INSERT INTO Spectator (Nume_Spectator, Email_Spectator, Telefon_Spectator) VALUES (?, ?, ?)";

            try (PreparedStatement psSpec = conn.prepareStatement(sqlSpectator, Statement.RETURN_GENERATED_KEYS)) {
                psSpec.setString(1, regNume);
                psSpec.setString(2, regEmail);
                psSpec.setString(3, regTelefon);

                int rows = psSpec.executeUpdate();
                if (rows > 0) {
                    try (ResultSet rsKeys = psSpec.getGeneratedKeys()) {
                        if (rsKeys.next()) {
                            idSpectatorNou = rsKeys.getInt(1);
                        }
                    }
                }
            }

            // Inserăm în UTILIZATOR (Username, Parola, Rol, ID_Spectator)
            if (idSpectatorNou > 0) {
                String sqlUser = "INSERT INTO Utilizator (Username, Parola, Rol, ID_Spectator) VALUES (?, ?, 'user', ?)";
                try (PreparedStatement psUser = conn.prepareStatement(sqlUser)) {
                    psUser.setString(1, regUsername);
                    psUser.setString(2, regPassword);
                    psUser.setInt(3, idSpectatorNou);
                    psUser.executeUpdate();
                }
                model.addAttribute("success", "Cont creat cu succes! Te poți autentifica.");
            } else {
                model.addAttribute("error", "Eroare la crearea profilului de spectator. Datele nu au fost salvate.");
            }

        } catch (SQLException ex) {
            ex.printStackTrace();
            if (ex.getMessage().contains("UNIQUE")) {
                model.addAttribute("error", "Acest email sau telefon există deja în baza de date!");
            } else {
                model.addAttribute("error", "Eroare DB: " + ex.getMessage());
            }
        }
        return "index";
    }

    @GetMapping("/logout")
    public String logout(HttpSession httpSession) {
        httpSession.invalidate();
        return "redirect:/";
    }

    // --- SPECTACOLE ---

    @GetMapping("/spectacole")
    public String spectacole(
            @RequestParam(value = "q", required = false, defaultValue = "") String search,
            @RequestParam(value = "sortBy", defaultValue = "DATA") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "ASC") String sortDir,
            HttpSession httpSession, Model model
    ) {
        Session ses = getSession(httpSession);
        if (ses == null) return "redirect:/";
        model.addAttribute("ses", ses);
        model.addAttribute("search", search);
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("sortDir", sortDir);

        Map<String, String> safeColumns = Map.of(
                "ID", "s.ID_Spectacol", "TITLU", "s.Titlu", "DATA", "s.Data_Spectacol",
                "ORA", "s.Ora", "PRET", "s.Pret_Bilet", "LOCATIE", "l.Nume_Locatie", "ORGANIZATOR", "o.Nume_Organizator"
        );
        String column = safeColumns.getOrDefault(sortBy.toUpperCase(), "s.Data_Spectacol");
        String direction = "DESC".equalsIgnoreCase(sortDir) ? "DESC" : "ASC";

        String sql = "SELECT s.ID_Spectacol, s.Titlu, s.Data_Spectacol, s.Ora, s.Pret_Bilet, " +
                "l.Nume_Locatie, o.Nume_Organizator, s.ID_Locatie, s.ID_Organizator " +
                "FROM Spectacol s LEFT JOIN Locatie l ON s.ID_Locatie = l.ID_Locatie LEFT JOIN Organizator o ON s.ID_Organizator = o.ID_Organizator ";

        boolean hasSearch = !search.isEmpty();
        if (hasSearch) sql += " WHERE s.Titlu LIKE ? OR l.Nume_Locatie LIKE ? OR o.Nume_Organizator LIKE ? ";

        sql += " ORDER BY " + column + " " + direction;

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");

        try (Connection conn = getDbConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            if (hasSearch) {
                String sVal = "%" + search + "%";
                ps.setString(1, sVal); ps.setString(2, sVal); ps.setString(3, sVal);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> spectacoleList = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> spectacol = new HashMap<>();
                    spectacol.put("id", rs.getInt(1));
                    spectacol.put("titlu", rs.getString(2));

                    Date dataDb = rs.getDate(3);
                    spectacol.put("data", dataDb != null ? sdf.format(dataDb) : "");

                    spectacol.put("ora", rs.getTime(4));
                    spectacol.put("pret", rs.getBigDecimal(5));
                    spectacol.put("locatie", rs.getString(6));
                    spectacol.put("organizator", rs.getString(7));
                    spectacol.put("id_locatie", rs.getInt(8));
                    spectacol.put("id_organizator", rs.getInt(9));

                    spectacoleList.add(spectacol);
                }
                model.addAttribute("spectacoleList", spectacoleList);
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            model.addAttribute("error", "Eroare: " + ex.getMessage());
        }
        return "spectacole";
    }

    @PostMapping("/spectacol/add")
    public String addSpectacol(@RequestParam Map<String, String> form, HttpSession httpSession, RedirectAttributes ra) {
        Session ses = getSession(httpSession);
        if (ses == null || !"admin".equalsIgnoreCase(ses.role)) { ra.addFlashAttribute("error", "Neautorizat."); return "redirect:/spectacole"; }

        try (Connection conn = getDbConnection()) {
            try { Integer.parseInt(form.getOrDefault("idloc", "0")); } catch (NumberFormatException e) { throw new RuntimeException("ID Locație trebuie să fie un număr valid!"); }
            try { Integer.parseInt(form.getOrDefault("idorg", "0")); } catch (NumberFormatException e) { throw new RuntimeException("ID Organizator trebuie să fie un număr valid!"); }
            try { new BigDecimal(form.getOrDefault("pret", "0")); } catch (Exception e) { throw new RuntimeException("Prețul trebuie să fie un număr valid!"); }

            try { Date.valueOf(form.getOrDefault("data", "")); } catch (Exception e) { throw new RuntimeException("Formatul datei este incorect! (Trebuie: YYYY-MM-DD)"); }
            String oraStr = form.getOrDefault("ora", "");
            if(oraStr.length() == 5) oraStr += ":00";
            try { Time.valueOf(oraStr); } catch (Exception e) { throw new RuntimeException("Formatul orei este incorect! (Trebuie: HH:MM:SS)"); }

            String sql = "INSERT INTO Spectacol (Titlu, Data_Spectacol, Ora, Pret_Bilet, ID_Locatie, ID_Organizator) VALUES (?,?,?,?,?,?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, form.getOrDefault("titlu", ""));
                ps.setDate(2, Date.valueOf(form.getOrDefault("data", "")));
                ps.setTime(3, Time.valueOf(oraStr));
                ps.setBigDecimal(4, new BigDecimal(form.getOrDefault("pret", "0")));
                ps.setInt(5, Integer.parseInt(form.getOrDefault("idloc", "0")));
                ps.setInt(6, Integer.parseInt(form.getOrDefault("idorg", "0")));
                ps.executeUpdate();
                ra.addFlashAttribute("success", "Spectacolul a fost adăugat!");
            }
        } catch (Exception ex) {
            String msg = ex.getMessage() != null ? ex.getMessage() : "Eroare necunoscută.";
            ra.addFlashAttribute("error", "Eroare: " + msg);
        }
        return "redirect:/spectacole";
    }

    @PostMapping("/spectacol/edit")
    public String editSpectacol(@RequestParam Map<String, String> form, HttpSession httpSession, RedirectAttributes ra) {
        Session ses = getSession(httpSession);
        if (ses == null || !"admin".equalsIgnoreCase(ses.role)) { ra.addFlashAttribute("error", "Neautorizat."); return "redirect:/spectacole"; }

        try (Connection conn = getDbConnection()) {
            int idLoc, idOrg;
            try { idLoc = Integer.parseInt(form.getOrDefault("idloc", "0")); } catch (NumberFormatException e) { throw new RuntimeException("ID-ul Locației introdus este invalid (trebuie număr)."); }
            try { idOrg = Integer.parseInt(form.getOrDefault("idorg", "0")); } catch (NumberFormatException e) { throw new RuntimeException("ID-ul Organizatorului introdus este invalid (trebuie număr)."); }
            try { new BigDecimal(form.getOrDefault("pret", "0")); } catch (Exception e) { throw new RuntimeException("Prețul introdus este invalid."); }

            try { Date.valueOf(form.getOrDefault("data", "")); } catch (Exception e) { throw new RuntimeException("Formatul datei este incorect! (Trebuie: YYYY-MM-DD)"); }

            String oraStr = form.getOrDefault("ora", "00:00:00");
            if(oraStr.length() == 5) oraStr += ":00";
            try { Time.valueOf(oraStr); } catch (Exception e) { throw new RuntimeException("Formatul orei este incorect! (Trebuie: HH:MM:SS)"); }

            String sql = "UPDATE Spectacol SET Titlu=?, Data_Spectacol=?, Ora=?, Pret_Bilet=?, ID_Locatie=?, ID_Organizator=? WHERE ID_Spectacol=?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, form.getOrDefault("titlu", ""));
                ps.setDate(2, Date.valueOf(form.getOrDefault("data", "2000-01-01")));
                ps.setTime(3, Time.valueOf(oraStr));
                ps.setBigDecimal(4, new BigDecimal(form.getOrDefault("pret", "0")));

                if (idLoc > 0) ps.setInt(5, idLoc); else ps.setNull(5, Types.INTEGER);
                if (idOrg > 0) ps.setInt(6, idOrg); else ps.setNull(6, Types.INTEGER);

                ps.setInt(7, Integer.parseInt(form.getOrDefault("id", "0")));
                ps.executeUpdate();
                ra.addFlashAttribute("success", "Editat cu succes!");
            }
        } catch (Exception ex) {
            String msg = ex.getMessage() != null ? ex.getMessage() : "Eroare necunoscută.";
            ra.addFlashAttribute("error", "Eroare: " + msg);
        }
        return "redirect:/spectacole";
    }

    @PostMapping("/spectacol/delete")
    public String deleteSpectacol(@RequestParam int id, HttpSession httpSession, RedirectAttributes ra) {
        Session ses = getSession(httpSession);
        if (ses == null || !"admin".equalsIgnoreCase(ses.role)) { ra.addFlashAttribute("error", "Neautorizat."); return "redirect:/spectacole"; }
        try (Connection conn = getDbConnection()) {
            try (PreparedStatement psBilet = conn.prepareStatement("DELETE FROM Bilet WHERE ID_Spectacol = ?")) {
                psBilet.setInt(1, id); psBilet.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM Spectacol WHERE ID_Spectacol=?")) {
                ps.setInt(1, id); ps.executeUpdate();
                ra.addFlashAttribute("success", "Șters!");
            }
        } catch (SQLException ex) { ra.addFlashAttribute("error", "Eroare: " + ex.getMessage()); }
        return "redirect:/spectacole";
    }

    @PostMapping("/bilet/buy")
    public String buyBilet(@RequestParam int id_spectacol, HttpSession httpSession, RedirectAttributes ra) {
        Session ses = getSession(httpSession);
        if (ses == null || ses.idSpectator == null) { ra.addFlashAttribute("error", "Nu poți cumpăra."); return "redirect:/spectacole"; }
        try (Connection conn = getDbConnection()) {
            String codBilet = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            String sql = "INSERT INTO Bilet (ID_Spectacol, ID_Spectator, Data_Cumparare, Cod_Bilet) VALUES (?, ?, CURRENT_TIMESTAMP, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, id_spectacol); ps.setInt(2, ses.idSpectator); ps.setString(3, codBilet);
                ps.executeUpdate();
                ra.addFlashAttribute("success", "Bilet cumpărat! Cod: " + codBilet);
            }
        } catch (SQLException ex) { ra.addFlashAttribute("error", "Eroare: " + ex.getMessage()); }
        return "redirect:/bilete";
    }

    // --- BILETE ---

    @GetMapping("/bilete")
    public String bilete(
            @RequestParam(value = "sortBy", defaultValue = "DATA") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "DESC") String sortDir,
            HttpSession httpSession, Model model
    ) {
        Session ses = getSession(httpSession);
        if (ses == null) return "redirect:/";
        model.addAttribute("ses", ses);
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("sortDir", sortDir);

        Map<String, String> safeColumns = Map.of("ID", "b.ID_Bilet", "TITLU", "s.Titlu", "SPECTATOR", "sp.Nume_Spectator", "DATA", "b.Data_Cumparare", "COD", "b.Cod_Bilet");
        String column = safeColumns.getOrDefault(sortBy.toUpperCase(), "b.Data_Cumparare");
        String direction = "DESC".equalsIgnoreCase(sortDir) ? "DESC" : "ASC";

        String sql = "SELECT b.ID_Bilet, s.Titlu, sp.Nume_Spectator, b.Data_Cumparare, b.Cod_Bilet " +
                "FROM Bilet b LEFT JOIN Spectacol s ON b.ID_Spectacol = s.ID_Spectacol LEFT JOIN Spectator sp ON b.ID_Spectator = sp.ID_Spectator ";
        if (!"admin".equalsIgnoreCase(ses.role)) sql += " WHERE b.ID_Spectator = ? ";
        sql += " ORDER BY " + column + " " + direction;

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");

        try (Connection conn = getDbConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                if (!"admin".equalsIgnoreCase(ses.role)) ps.setInt(1, ses.idSpectator);
                try (ResultSet rs = ps.executeQuery()) {
                    List<Map<String, Object>> bileteList = new ArrayList<>();
                    while (rs.next()) {
                        Map<String, Object> bilet = new HashMap<>();
                        bilet.put("id", rs.getInt(1)); bilet.put("spectacol", rs.getString(2));
                        bilet.put("spectator", rs.getString(3));

                        Timestamp ts = rs.getTimestamp(4);
                        bilet.put("data_cumparare", ts != null ? sdf.format(ts) : "");

                        bilet.put("cod", rs.getString(5)); bileteList.add(bilet);
                    }
                    model.addAttribute("bileteList", bileteList);
                }
            }
            if ("admin".equalsIgnoreCase(ses.role)) {
                String sqlRecent = "SELECT TOP 3 b.Cod_Bilet, s.Titlu, sp.Nume_Spectator FROM Bilet b " +
                        "JOIN Spectacol s ON b.ID_Spectacol = s.ID_Spectacol JOIN Spectator sp ON b.ID_Spectator = sp.ID_Spectator ORDER BY b.Data_Cumparare DESC";
                try (Statement st = conn.createStatement(); ResultSet rsRec = st.executeQuery(sqlRecent)) {
                    List<Map<String, Object>> recentTickets = new ArrayList<>();
                    while (rsRec.next()) {
                        Map<String, Object> m = new HashMap<>();
                        m.put("cod", rsRec.getString("Cod_Bilet"));
                        m.put("info", rsRec.getString("Titlu") + " -> " + rsRec.getString("Nume_Spectator"));
                        recentTickets.add(m);
                    }
                    model.addAttribute("recentTickets", recentTickets);
                }
            }
        } catch (SQLException ex) { ex.printStackTrace(); }
        return "bilete";
    }

    // --- RAPORT  ---

    @GetMapping("/raport")
    public String raport(
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            HttpSession httpSession, Model model
    ) {
        Session ses = getSession(httpSession);
        if (ses == null || !"admin".equalsIgnoreCase(ses.role)) return "redirect:/spectacole";
        model.addAttribute("ses", ses);

        if (startDate == null || startDate.isEmpty()) startDate = "2024-01-01";
        if (endDate == null || endDate.isEmpty()) endDate = "2030-12-31";
        model.addAttribute("startDate", startDate); model.addAttribute("endDate", endDate);

        try (Connection conn = getDbConnection()) {

            // COMPLEXA 1
            String sql1 = "SELECT T.Titlu, T.Nr, T.Total FROM " +
                    "(SELECT s.Titlu, COUNT(b.ID_Bilet) AS Nr, SUM(s.Pret_Bilet) AS Total " +
                    " FROM Spectacol s LEFT JOIN Bilet b ON s.ID_Spectacol = b.ID_Spectacol " +
                    " WHERE s.Data_Spectacol BETWEEN ? AND ? " +
                    " GROUP BY s.ID_Spectacol, s.Titlu) AS T " +
                    "WHERE T.Nr > 0 ORDER BY T.Total DESC";

            List<Map<String, Object>> listaVanzari = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql1)) {
                ps.setDate(1, Date.valueOf(startDate)); ps.setDate(2, Date.valueOf(endDate));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> m = new HashMap<>(); m.put("titlu", rs.getString("Titlu"));
                        m.put("nr", rs.getInt("Nr")); m.put("total", rs.getBigDecimal("Total")); listaVanzari.add(m);
                    }
                }
            }
            model.addAttribute("listaVanzari", listaVanzari);

            // COMPLEXA 2
            String sql2 = "SELECT o.Nume_Organizator, SUM(s.Pret_Bilet) AS Total " +
                    "FROM Organizator o LEFT JOIN Spectacol s ON o.ID_Organizator=s.ID_Organizator " +
                    "LEFT JOIN Bilet b ON s.ID_Spectacol=b.ID_Spectacol " +
                    "WHERE o.ID_Organizator IN (SELECT ID_Organizator FROM Spectacol) " +
                    "GROUP BY o.Nume_Organizator " +
                    "HAVING SUM(s.Pret_Bilet) > 0 " +
                    "ORDER BY Total DESC";

            List<Map<String, Object>> topOrg = new ArrayList<>();
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql2)) {
                while (rs.next()) {
                    Map<String, Object> m = new HashMap<>(); m.put("nume", rs.getString(1)); m.put("total", rs.getBigDecimal(2)); topOrg.add(m);
                }
            }
            model.addAttribute("topOrganizatori", topOrg);

            // COMPLEXA 3
            String sql3 = "SELECT sp.Nume_Spectator, " +
                    "(SELECT COUNT(*) FROM Bilet b WHERE b.ID_Spectator = sp.ID_Spectator) AS Cnt " +
                    "FROM Spectator sp " +
                    "WHERE sp.ID_Spectator = (SELECT TOP 1 ID_Spectator FROM Bilet GROUP BY ID_Spectator ORDER BY COUNT(*) DESC)";

            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql3)) {
                if (rs.next()) model.addAttribute("fidel", rs.getString(1) + " (" + rs.getInt(2) + " bilete)");
                else model.addAttribute("fidel", "Niciunul");
            }

            // COMPLEXA 4
            String sql4 = "SELECT l.Nume_Locatie, l.Capacitate, " +
                    "(SELECT COUNT(*) FROM Spectacol s WHERE s.ID_Locatie = l.ID_Locatie) AS NrShow, " +
                    "(SELECT COUNT(*) FROM Bilet b JOIN Spectacol s ON b.ID_Spectacol=s.ID_Spectacol WHERE s.ID_Locatie=l.ID_Locatie) AS Vandute " +
                    "FROM Locatie l WHERE l.Capacitate > 0";
            List<Map<String, Object>> grad = new ArrayList<>();
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql4)) {
                while (rs.next()) {
                    String nume = rs.getString(1);
                    int capacitatePerShow = rs.getInt(2);
                    int nrShow = rs.getInt(3);
                    int bileteVanduteTotal = rs.getInt(4);

                    double proc = 0.0;
                    if (nrShow > 0 && capacitatePerShow > 0) {
                        long capacitateTotala = (long) capacitatePerShow * nrShow;
                        proc = ((double) bileteVanduteTotal / capacitateTotala) * 100;
                    }

                    if (nrShow > 0) {
                        Map<String, Object> m = new HashMap<>(); m.put("nume", nume);
                        m.put("procent", String.format("%.2f %%", proc));
                        grad.add(m);
                    }
                }
            }
            model.addAttribute("gradOcupare", grad);

            String sql6 = "SELECT TOP 5 s.Titlu, l.Nume_Locatie FROM Spectacol s JOIN Locatie l ON s.ID_Locatie=l.ID_Locatie ORDER BY s.ID_Spectacol DESC";
            List<String> checkList = new ArrayList<>();
            try(Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql6)){
                while(rs.next()) checkList.add(rs.getString(1) + " (în " + rs.getString(2) + ")");
            }
            model.addAttribute("checkList", checkList);

            model.addAttribute("titluCheckList", "Listă control rapidă (ultimele 5 showuri+locație)");

        } catch (Exception ex) { ex.printStackTrace(); model.addAttribute("error", ex.getMessage()); }
        return "raport";
    }

    // --- ARTIȘTI ---

    @GetMapping("/artisti")
    public String artisti(
            @RequestParam(value = "q", required = false, defaultValue = "") String search,
            @RequestParam(value = "sortBy", defaultValue = "NUME") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "ASC") String sortDir,
            HttpSession httpSession, Model model
    ) {
        Session ses = getSession(httpSession);
        if (ses == null) return "redirect:/";
        model.addAttribute("ses", ses);
        model.addAttribute("search", search);
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("sortDir", sortDir);

        Map<String, String> safeColumns = Map.of("ID", "ID_Artist", "NUME", "Nume_Artist", "PRENUME", "Prenume_Artist", "NATIONALITATE", "Nationalitate", "VARSTA", "Varsta", "EXPERIENTA", "Experienta_Ani");
        String column = safeColumns.getOrDefault(sortBy.toUpperCase(), "Nume_Artist");
        String direction = "DESC".equalsIgnoreCase(sortDir) ? "DESC" : "ASC";

        String sql = "SELECT ID_Artist, Nume_Artist, Prenume_Artist, Nationalitate, Varsta, Experienta_Ani FROM Artist";
        boolean hasSearch = !search.isEmpty();
        if (hasSearch) sql += " WHERE Nume_Artist LIKE ? OR Prenume_Artist LIKE ? OR Nationalitate LIKE ?";
        sql += " ORDER BY " + column + " " + direction;

        try (Connection conn = getDbConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            if (hasSearch) { String sVal = "%" + search + "%"; ps.setString(1, sVal); ps.setString(2, sVal); ps.setString(3, sVal); }
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> artistiList = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> artist = new HashMap<>();
                    artist.put("id", rs.getInt(1));
                    artist.put("nume", rs.getString(2));
                    artist.put("prenume", rs.getString(3));
                    artist.put("nationalitate", rs.getString(4));
                    artist.put("varsta", rs.getInt(5));
                    artist.put("experienta", rs.getInt(6));
                    artistiList.add(artist);
                }
                model.addAttribute("artistiList", artistiList);
            }
        } catch (SQLException ex) { ex.printStackTrace(); model.addAttribute("error", "Eroare: " + ex.getMessage()); }
        return "artisti";
    }

    @PostMapping("/artist/add")
    public String addArtist(@RequestParam Map<String, String> form, HttpSession httpSession, RedirectAttributes ra) {
        Session ses = getSession(httpSession);
        if (ses == null || !"admin".equalsIgnoreCase(ses.role)) { ra.addFlashAttribute("error", "Neautorizat."); return "redirect:/artisti"; }
        try (Connection conn = getDbConnection()) {
            if (form.getOrDefault("nationalitate", "").matches(".*\\d.*")) throw new RuntimeException("Naționalitatea nu poate conține cifre!");

            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO Artist (Nume_Artist, Prenume_Artist, Nationalitate, Varsta, Experienta_Ani) VALUES (?,?,?,?,?)")) {
                ps.setString(1, form.getOrDefault("nume", "")); ps.setString(2, form.getOrDefault("prenume", "")); ps.setString(3, form.getOrDefault("nationalitate", ""));
                int varsta = 0; try { varsta = Integer.parseInt(form.getOrDefault("varsta", "0")); } catch(Exception e){}
                int exp = 0; try { exp = Integer.parseInt(form.getOrDefault("experienta", "0")); } catch(Exception e){}
                ps.setInt(4, varsta); ps.setInt(5, exp); ps.executeUpdate();
                ra.addFlashAttribute("success", "Adăugat!");
            }
        } catch (Exception ex) { ra.addFlashAttribute("error", "Eroare: " + ex.getMessage()); }
        return "redirect:/artisti";
    }

    @PostMapping("/artist/edit")
    public String editArtist(@RequestParam Map<String, String> form, HttpSession httpSession, RedirectAttributes ra) {
        Session ses = getSession(httpSession);
        if (ses == null || !"admin".equalsIgnoreCase(ses.role)) { ra.addFlashAttribute("error", "Neautorizat."); return "redirect:/artisti"; }
        try (Connection conn = getDbConnection()) {
            if (form.getOrDefault("nationalitate", "").matches(".*\\d.*")) throw new RuntimeException("Naționalitatea nu poate conține cifre!");

            try (PreparedStatement ps = conn.prepareStatement("UPDATE Artist SET Nume_Artist=?, Prenume_Artist=?, Nationalitate=?, Varsta=?, Experienta_Ani=? WHERE ID_Artist=?")) {
                ps.setString(1, form.get("nume")); ps.setString(2, form.get("prenume")); ps.setString(3, form.get("nationalitate"));
                ps.setInt(4, Integer.parseInt(form.get("varsta"))); ps.setInt(5, Integer.parseInt(form.get("experienta"))); ps.setInt(6, Integer.parseInt(form.get("id")));
                ps.executeUpdate(); ra.addFlashAttribute("success", "Editat!");
            }
        } catch (Exception ex) { ra.addFlashAttribute("error", "Eroare: " + ex.getMessage()); }
        return "redirect:/artisti";
    }

    @PostMapping("/artist/delete")
    public String deleteArtist(@RequestParam int id, HttpSession httpSession, RedirectAttributes ra) {
        Session ses = getSession(httpSession);
        if (ses == null || !"admin".equalsIgnoreCase(ses.role)) { ra.addFlashAttribute("error", "Neautorizat."); return "redirect:/artisti"; }
        try (Connection conn = getDbConnection(); PreparedStatement ps = conn.prepareStatement("DELETE FROM Artist WHERE ID_Artist=?")) {
            ps.setInt(1, id); ps.executeUpdate(); ra.addFlashAttribute("success", "Șters!");
        } catch (SQLException ex) { ra.addFlashAttribute("error", "Eroare: " + ex.getMessage()); }
        return "redirect:/artisti";
    }

    // --- LOCAȚII ---

    @GetMapping("/locatii")
    public String locatii(
            @RequestParam(value = "q", required = false, defaultValue = "") String search,
            @RequestParam(value = "sortBy", defaultValue = "NUME") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "ASC") String sortDir,
            HttpSession httpSession, Model model
    ) {
        Session ses = getSession(httpSession);
        if (ses == null) return "redirect:/";
        model.addAttribute("ses", ses);
        model.addAttribute("search", search);
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("sortDir", sortDir);

        Map<String, String> safeColumns = Map.of("ID", "ID_Locatie", "NUME", "Nume_Locatie", "ADRESA", "Adresa", "ORAS", "Oras", "CAPACITATE", "Capacitate");
        String column = safeColumns.getOrDefault(sortBy.toUpperCase(), "Nume_Locatie");
        String direction = "DESC".equalsIgnoreCase(sortDir) ? "DESC" : "ASC";

        String sql = "SELECT ID_Locatie, Nume_Locatie, Adresa, Oras, Capacitate FROM Locatie";
        boolean hasSearch = !search.isEmpty();
        if (hasSearch) sql += " WHERE Nume_Locatie LIKE ? OR Adresa LIKE ? OR Oras LIKE ?";
        sql += " ORDER BY " + column + " " + direction;

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");

        try (Connection conn = getDbConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                if (hasSearch) { String sVal = "%" + search + "%"; ps.setString(1, sVal); ps.setString(2, sVal); ps.setString(3, sVal); }
                try (ResultSet rs = ps.executeQuery()) {
                    List<Map<String, Object>> locatiiList = new ArrayList<>();
                    while (rs.next()) {
                        Map<String, Object> l = new HashMap<>();
                        l.put("id", rs.getInt(1)); l.put("nume", rs.getString(2)); l.put("adresa", rs.getString(3));
                        l.put("oras", rs.getString(4)); l.put("capacitate", rs.getInt(5)); locatiiList.add(l);
                    }
                    model.addAttribute("locatiiList", locatiiList);
                }
            }
            String sqlUp = "SELECT TOP 5 l.Nume_Locatie, s.Titlu, s.Data_Spectacol FROM Locatie l JOIN Spectacol s ON l.ID_Locatie=s.ID_Locatie WHERE s.Data_Spectacol >= CAST(GETDATE() AS DATE) ORDER BY s.Data_Spectacol ASC";
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sqlUp)) {
                List<Map<String, Object>> up = new ArrayList<>();
                while(rs.next()) {
                    Map<String, Object> m = new HashMap<>(); m.put("loc", rs.getString(1)); m.put("show", rs.getString(2));
                    Date dbDate = rs.getDate(3);
                    m.put("data", dbDate != null ? sdf.format(dbDate) : "");
                    up.add(m);
                }
                model.addAttribute("upcomingShows", up);
            }
        } catch (SQLException ex) { ex.printStackTrace(); }
        return "locatii";
    }

    @PostMapping("/locatie/add")
    public String addLocatie(@RequestParam Map<String, String> form, HttpSession httpSession, RedirectAttributes ra) {
        Session ses = getSession(httpSession);
        if (ses == null || !"admin".equalsIgnoreCase(ses.role)) return "redirect:/locatii";
        try (Connection conn = getDbConnection()) {
            if (form.getOrDefault("oras", "").matches(".*\\d.*")) throw new RuntimeException("Orașul nu poate conține cifre!");

            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO Locatie (Nume_Locatie, Adresa, Oras, Capacitate) VALUES (?,?,?,?)")) {
                ps.setString(1, form.get("nume")); ps.setString(2, form.get("adresa")); ps.setString(3, form.get("oras")); ps.setInt(4, Integer.parseInt(form.get("capacitate")));
                ps.executeUpdate(); ra.addFlashAttribute("success", "Adăugat!");
            }
        } catch (Exception ex) { ra.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/locatii";
    }

    @PostMapping("/locatie/edit")
    public String editLocatie(@RequestParam Map<String, String> f, HttpSession s, RedirectAttributes r) {
        if(getSession(s)==null || !"admin".equalsIgnoreCase(getSession(s).role)) return "redirect:/locatii";
        try(Connection c=getDbConnection()){
            if (f.getOrDefault("oras", "").matches(".*\\d.*")) throw new RuntimeException("Orașul nu poate conține cifre!");

            try (PreparedStatement ps=c.prepareStatement("UPDATE Locatie SET Nume_Locatie=?, Adresa=?, Oras=?, Capacitate=? WHERE ID_Locatie=?")){
                ps.setString(1,f.get("nume")); ps.setString(2,f.get("adresa")); ps.setString(3,f.get("oras"));
                ps.setInt(4,Integer.parseInt(f.get("capacitate"))); ps.setInt(5,Integer.parseInt(f.get("id"))); ps.executeUpdate(); r.addFlashAttribute("success","Editat!");
            }
        }catch(Exception e){r.addFlashAttribute("error",e.getMessage());} return "redirect:/locatii";
    }

    @PostMapping("/locatie/delete")
    public String deleteLocatie(@RequestParam int id, HttpSession s, RedirectAttributes r) {
        if(getSession(s)==null || !"admin".equalsIgnoreCase(getSession(s).role)) return "redirect:/locatii";
        try(Connection c=getDbConnection(); PreparedStatement ps=c.prepareStatement("DELETE FROM Locatie WHERE ID_Locatie=?")){
            ps.setInt(1,id); ps.executeUpdate(); r.addFlashAttribute("success","Șters!");
        }catch(Exception e){r.addFlashAttribute("error",e.getMessage());} return "redirect:/locatii";
    }

    // --- ORGANIZATORI ---

    @GetMapping("/organizatori")
    public String organizatori(
            @RequestParam(value = "q", required = false, defaultValue = "") String search,
            @RequestParam(value = "sortBy", defaultValue = "NUME") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "ASC") String sortDir,
            HttpSession httpSession, Model model
    ) {
        Session ses = getSession(httpSession);
        if (ses == null) return "redirect:/";
        model.addAttribute("ses", ses);
        model.addAttribute("search", search);
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("sortDir", sortDir);

        Map<String, String> safeColumns = Map.of("ID", "ID_Organizator", "NUME", "Nume_Organizator", "EMAIL", "Email_Organizator", "TELEFON", "Telefon_Organizator");
        String column = safeColumns.getOrDefault(sortBy.toUpperCase(), "Nume_Organizator");
        String direction = "DESC".equalsIgnoreCase(sortDir) ? "DESC" : "ASC";

        String sql = "SELECT ID_Organizator, Nume_Organizator, Email_Organizator, Telefon_Organizator FROM Organizator";
        boolean hasSearch = !search.isEmpty();
        if (hasSearch) sql += " WHERE Nume_Organizator LIKE ? OR Email_Organizator LIKE ? OR Telefon_Organizator LIKE ?";
        sql += " ORDER BY " + column + " " + direction;

        try (Connection conn = getDbConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                if (hasSearch) { String sVal = "%" + search + "%"; ps.setString(1, sVal); ps.setString(2, sVal); ps.setString(3, sVal); }
                try (ResultSet rs = ps.executeQuery()) {
                    List<Map<String, Object>> list = new ArrayList<>();
                    while (rs.next()) {
                        Map<String, Object> o = new HashMap<>();
                        o.put("id", rs.getInt(1)); o.put("nume", rs.getString(2)); o.put("email", rs.getString(3)); o.put("telefon", rs.getString(4)); list.add(o);
                    }
                    model.addAttribute("organizatoriList", list);
                }
            }
            String sqlRec = "SELECT TOP 5 o.Nume_Organizator, s.Titlu FROM Organizator o JOIN Spectacol s ON o.ID_Organizator=s.ID_Organizator ORDER BY s.Data_Spectacol DESC";
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sqlRec)) {
                List<Map<String, Object>> rec = new ArrayList<>();
                while(rs.next()) { Map<String,Object> m=new HashMap<>(); m.put("org", rs.getString(1)); m.put("show", rs.getString(2)); rec.add(m); }
                model.addAttribute("recentShows", rec);
            }
        } catch (SQLException ex) { ex.printStackTrace(); }
        return "organizatori";
    }

    @PostMapping("/organizator/add")
    public String addOrganizator(@RequestParam Map<String, String> f, HttpSession s, RedirectAttributes r) {
        if(getSession(s)==null || !"admin".equalsIgnoreCase(getSession(s).role)) return "redirect:/organizatori";
        try(Connection c=getDbConnection()){
            if (!f.getOrDefault("email", "").contains("@gmail.com")) throw new RuntimeException("Email-ul trebuie să fie de tip @gmail.com!");
            if (!f.getOrDefault("telefon", "").matches("\\d{10}")) throw new RuntimeException("Telefonul trebuie să aibă exact 10 cifre!");

            try (PreparedStatement ps=c.prepareStatement("INSERT INTO Organizator (Nume_Organizator, Email_Organizator, Telefon_Organizator) VALUES (?,?,?)")){
                ps.setString(1,f.get("nume")); ps.setString(2,f.get("email")); ps.setString(3,f.get("telefon")); ps.executeUpdate(); r.addFlashAttribute("success","Adăugat!");
            }
        }catch(Exception e){r.addFlashAttribute("error",e.getMessage());} return "redirect:/organizatori";
    }

    @PostMapping("/organizator/edit")
    public String editOrganizator(@RequestParam Map<String, String> f, HttpSession s, RedirectAttributes r) {
        if(getSession(s)==null || !"admin".equalsIgnoreCase(getSession(s).role)) return "redirect:/organizatori";
        try(Connection c=getDbConnection()){
            if (!f.getOrDefault("email", "").contains("@gmail.com")) throw new RuntimeException("Email-ul trebuie să fie de tip @gmail.com!");
            if (!f.getOrDefault("telefon", "").matches("\\d{10}")) throw new RuntimeException("Telefonul trebuie să aibă exact 10 cifre!");

            try (PreparedStatement ps=c.prepareStatement("UPDATE Organizator SET Nume_Organizator=?, Email_Organizator=?, Telefon_Organizator=? WHERE ID_Organizator=?")){
                ps.setString(1,f.get("nume")); ps.setString(2,f.get("email")); ps.setString(3,f.get("telefon")); ps.setInt(4,Integer.parseInt(f.get("id"))); ps.executeUpdate(); r.addFlashAttribute("success","Editat!");
            }
        }catch(Exception e){r.addFlashAttribute("error",e.getMessage());} return "redirect:/organizatori";
    }

    @PostMapping("/organizator/delete")
    public String deleteOrganizator(@RequestParam int id, HttpSession s, RedirectAttributes r) {
        if(getSession(s)==null || !"admin".equalsIgnoreCase(getSession(s).role)) return "redirect:/organizatori";
        try(Connection c=getDbConnection(); PreparedStatement ps=c.prepareStatement("DELETE FROM Organizator WHERE ID_Organizator=?")){
            ps.setInt(1,id); ps.executeUpdate(); r.addFlashAttribute("success","Șters!");
        }catch(Exception e){r.addFlashAttribute("error",e.getMessage());} return "redirect:/organizatori";
    }
}