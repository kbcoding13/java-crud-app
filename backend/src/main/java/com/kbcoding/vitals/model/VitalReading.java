package com.kbcoding.vitals.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.Check;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

@Table(name = "vital_readings")
@Entity
public class VitalReading {


    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "vital_reading_seq")
    @SequenceGenerator(name = "vital_reading_seq", sequenceName = "vital_reading_seq", allocationSize = 50)
    private Long id;

    @NotBlank
    @Column(length = 10, nullable = false)
    private String patientId;

    @Min(20)
    @Max(300)
    @Column(nullable = false)
    @NotNull
    private Integer heartRate;

    @Min(50)
    @Max(300)
    @Column(nullable = false)
    @NotNull
    private Integer systolic;

    @Min(30)
    @Max(200)
    @Column(nullable = false)
    @NotNull
    private Integer diastolic;

    @DecimalMin("25.0")
    @DecimalMax("45.0")
    @Column(nullable = false)
    @NotNull
=    private Double temperatureCelsius;

    @PastOrPresent
    @Column(nullable = false)
    @NotNull
    private LocalDateTime recordedAt;


    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public String getPatientId() {
        return patientId;
    }
    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }
    public Integer getHeartRate() {
        return heartRate;
    }
    public void setHeartRate(Integer heartRate) {
        this.heartRate = heartRate;
    }
    public Integer getSystolic() {
        return systolic;
    }
    public void setSystolic(Integer systolic) {
        this.systolic = systolic;
    }
    public Integer getDiastolic() {
        return diastolic;
    }
    public void setDiastolic(Integer diastolic) {
        this.diastolic = diastolic;
    }
    public Double getTemperatureCelsius() {
        return temperatureCelsius;
    }
    public void setTemperatureCelsius(Double temperatureCelsius) {
        this.temperatureCelsius = temperatureCelsius;
    }
    public LocalDateTime getRecordedAt() {
        return recordedAt;
    }
    public void setRecordedAt(LocalDateTime recordedAt) {
        this.recordedAt = recordedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VitalReading other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return VitalReading.class.hashCode();
    }

}
