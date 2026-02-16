/** Clasa pentru pornirea aplicatiei si configurarea Spring Boot
 * @author Sova Ioan-Rares
 * @version 25 Decembrie 2025
 */

package aplicatie_standup.app_standup;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SpectacoleApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpectacoleApplication.class, args);
    }
}