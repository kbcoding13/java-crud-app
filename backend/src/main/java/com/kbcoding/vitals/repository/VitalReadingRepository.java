package com.kbcoding.vitals.repository;

import com.kbcoding.vitals.model.VitalReading;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VitalReadingRepository extends JpaRepository<VitalReading, Long> {

    List<VitalReading> findByPatientId(String patientId);

    List<VitalReading> findAllByOrderByRecordedAtDesc();
}



