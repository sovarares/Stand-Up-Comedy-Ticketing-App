## 🛠️ Tech Stack

**Backend:** Java, Spring Boot (MVC Architecture)
**Database:** Microsoft SQL Server (MSSQL)
**Data Access:** Native JDBC (Raw SQL for complex reporting & optimization)
**Frontend:** Thymeleaf, HTML, CSS, Bootstrap
**Tools:** Maven, Git

## 📂 Project Documentation

This repository includes detailed documentation files located in the root directory:
* `Documentation 1.pdf` & `Documentation 2.pdf` - Full project specifications and architectural details.
* `queries.pdf` - The complex SQL queries used for generating reports (Joins, Subqueries, Aggregations).
* `SpectacoleDeStandUp.bak` - Database backup file for quick restoration.

## ⚙️ Setup & Installation

1.  **Clone the repository:**
    ```bash
    git clone [https://github.com/sovarares/Stand-Up-Comedy-Ticketing-App.git](https://github.com/sovarares/Stand-Up-Comedy-Ticketing-App.git)
    ```
2.  **Database Setup:**
    * Restore the `SpectacoleDeStandUp.bak` file in MS SQL Server Management Studio (SSMS).
      
3.  **Configure Application:**
    * Open `src/main/resources/application.properties`.
    * Update the `spring.datasource.url`, `username`, and `password` to match your local SQL Server instance.
      
4.  **Run the App:**
    * Run the project as a Spring Boot App via your IDE (IntelliJ/Eclipse) or using Maven:
    ```bash
    mvn spring-boot:run
    ```
5.  **Access:**
    * Go to `http://localhost:8080` in your browser.

*Developed by Șova Ioan-Rareș*
