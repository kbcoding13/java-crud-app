package com.kbcoding.vitals.service;

import com.kbcoding.vitals.model.VitalReading;
import com.kbcoding.vitals.exception.VitalNotFoundException;
import org.springframework.stereotype.Service;
import com.kbcoding.vitals.repository.VitalReadingRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class VitalReadingService {
    
    private final VitalReadingRepository repository;

    public VitalReadingService(VitalReadingRepository repository) {
        this.repository = repository;
    };

    public List<VitalReading> findAll() {
        return repository.findAllByOrderByRecordedAtDesc();
    };

    public List<VitalReading> findByPatientId (String patientId) {
        return repository.findByPatientId(patientId);
    };

    public VitalReading getById (Long id){
        return repository.findById(id).orElseThrow(() -> new VitalNotFoundException("Vital reading not found: " + id));
    };

    public VitalReading create(VitalReading reading) {
        reading.setRecordedAt(LocalDateTime.now());
        return repository.save(reading);
    };


    public VitalReading update (Long id, VitalReading incoming) {
        VitalReading existing = getById(id);
        existing.setPatientId(incoming.getPatientId());
        existing.setHeartRate(incoming.getHeartRate());
        existing.setSystolic(incoming.getSystolic());
        existing.setDiastolic(incoming.getDiastolic());
        existing.setTemperatureCelsius(incoming.getTemperatureCelsius());
        return repository.save(existing);
    };

    public void delete (Long id) {
        if (!repository.existsById(id)) {
            throw new VitalNotFoundException("Vital Reading not found: " + id);
        };
        repository.deleteById(id);
    };
};
