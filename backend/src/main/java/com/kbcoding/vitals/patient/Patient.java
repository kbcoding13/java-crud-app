package com.kbcoding.vitals.patient;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.Objects;




@Entity
@Table(name = "patients", 
    uniqueConstraints = @UniqueConstraint(name = "uk_patients_nhi", columnNames = "nhi"))

public class Patient {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Patient other)) return false;
        return nhi != null && nhi.equals(other.nhi);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nhi);
    }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "patient_seq")
    @SequenceGenerator(name = "patient_seq", sequenceName = "patient_seq", allocationSize = 50)
    private Long id;

    @NotBlank
    @Column(nullable = false, unique = false, length = 50)
    private String name;

    @Column(nullable = false, length = 7)
    @NotBlank
    @Pattern(regexp = "[A-Z]{3}[0-9]{4}")
    private String nhi;

    @NotNull
    @Column(nullable = false)
    @PastOrPresent
    private LocalDate dob;

    @Email
    @Column(nullable = true, unique = false, length = 50)
    private String email;

    @Column(length = 50)
    private String ward;





    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }

    
    public String getNhi() {
        return nhi;
    }

    public void setNhi(String nhi) {
        this.nhi = nhi;
    }


    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }


    public LocalDate getDob() {
        return dob;
    }
    public void setDob(LocalDate dob) {
        this.dob = dob;
    }


    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }


    public String getWard() {
        return ward;
    }
    public void setWard(String ward) {
        this.ward = ward;
    }

}
