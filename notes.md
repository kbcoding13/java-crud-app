H2 — an in-memory database that vanishes when the process stops. Argument for: your tests run in milliseconds with zero setup and never leave junk in a real DB. Argument against: H2 is not Postgres. Different SQL dialect, different constraint behaviour. Tests can pass on H2 and the same code break in production. Your call: include it or test against real Postgres?

Validation — gives you @NotBlank, @Email, @Past and a way to reject bad input. Your Patient has an email and a dob. Question before you answer: which layer should reject a patient with a malformed email — controller, service, entity, or database? Name a reason for each candidate before you pick.

DevTools — restarts the app automatically when a class changes. Low stakes, convenience only.

Lombok — an annotation processor. You write @Getter @Setter and it generates the methods into the bytecode at compile time. Why it's contested: the source you read isn't the code that runs, it needs an IDE plugin, and specifically @Data on a JPA entity generates equals/hashCode/toString that touch every field — including lazy @ManyToOne relationships, which can trigger surprise queries or infinite recursion. You hit @ManyToOne in step 2. My recommendation: skip it for now. Write the getters by hand, feel the pain, then decide if it's worth the magic. (And no, Java records don't rescue you here — JPA entities need a no-arg constructor and mutable fields, which records don't have



Use of Package-by-feature: Allows for files to be package-private meaning files like AlertService doesn't have to physically reach the service layer and hit the patient database directly.

LAYERS
Controller: Speaking HTTP, URLs, status codes, JSON object - @RestController
Service: Speaking in business rules, "reading out of range" equals an alert - @Service
Repository: Speaks persistence, finding, saving, deleting - JpaRepository
Database: Speaks constraints, the last line of defense - NOT NULL, UNIQUE
Entity: It's not a layer, it's a data that surfs across the layers and trnsmits?

The repository is built to just fetch and store content in terms of validating whether an input has been said twice that is up to the service layer

Spring boot -> Sees H2 on the classpath -> silently configures an in-memory database.

