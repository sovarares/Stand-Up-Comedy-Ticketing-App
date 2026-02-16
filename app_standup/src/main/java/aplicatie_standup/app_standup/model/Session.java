// src/main/java/com/example/spectacole/model/Session.java
package aplicatie_standup.app_standup.model;

/**
 * Reprezinta datele de baza ale sesiunii utilizatorului (logat).
 */
public class Session {
    public final String username;
    public final String role;
    // Poate fi null daca utilizatorul nu este un Spectator (ex: Admin)
    public final Integer idSpectator;

    public Session(String username, String role, Integer idSpectator) {
        this.username = username;
        this.role = role;
        this.idSpectator = idSpectator;
    }

    public String getUsername() { return username; }
    public String getRole() { return role; }
    public Integer getIdSpectator() { return idSpectator; }
}