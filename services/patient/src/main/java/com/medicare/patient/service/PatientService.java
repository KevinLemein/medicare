package com.medicare.patient.service;

import com.medicare.patient.common.DuplicateResourceException;
import com.medicare.patient.common.ResourceNotFoundException;
import com.medicare.patient.dto.*;
import com.medicare.patient.entity.Allergy;
import com.medicare.patient.entity.EmergencyContact;
import org.springframework.security.access.AccessDeniedException;
import com.medicare.patient.entity.Patient;
import com.medicare.patient.repository.PatientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PatientService {

    private final PatientRepository patientRepository;

    public PatientService(PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
    }

    @Transactional
    public PatientProfileResponse createProfile(CreatePatientProfileRequest request, UUID userId) {
        if (patientRepository.findByUserId(userId).isPresent()) {
            throw new DuplicateResourceException("A patient profile already exists for this account");
        }
        Patient patient = Patient.builder()
                .userId(userId)
                .dateOfBirth(request.dateOfBirth())
                .phoneNumber(request.phoneNumber())
                .address(request.address())
                .build();
        patientRepository.save(patient);
        return toResponse(patient);
    }

    @Transactional(readOnly = true)
    public PatientProfileResponse getMyProfile(UUID userId) {
        Patient patient = patientRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No patient profile found for this account"));
        return toResponse(patient);
    }

    private PatientProfileResponse toResponse(Patient patient) {
        var contacts = patient.getEmergencyContacts().stream()
                .map(c -> new PatientProfileResponse.EmergencyContactView(c.getId(), c.getName(), c.getPhone(), c.getRelationshipType()))
                .collect(Collectors.toList());
        var allergies = patient.getAllergies().stream()
                .map(a -> new PatientProfileResponse.AllergyView(a.getId(), a.getAllergen(), a.getNotes()))
                .collect(Collectors.toList());
        return new PatientProfileResponse(
                patient.getId(), patient.getDateOfBirth(), patient.getPhoneNumber(),
                patient.getAddress(), contacts, allergies
        );
    }

    @Transactional
    public EmergencyContactResponse addEmergencyContact(EmergencyContactRequest request, UUID userId) {
        Patient patient = getOwnedPatient(userId);
        EmergencyContact contact = EmergencyContact.builder()
                .patient(patient)
                .name(request.name())
                .phone(request.phone())
                .relationshipType(request.relationshipType())
                .build();
        patient.getEmergencyContacts().add(contact);
        patientRepository.flush();
        return new EmergencyContactResponse(contact.getId(), contact.getName(), contact.getPhone(), contact.getRelationshipType());
    }

    @Transactional
    public void removeEmergencyContact(UUID contactId, UUID userId) {
        Patient patient = getOwnedPatient(userId);
        EmergencyContact contact = patient.getEmergencyContacts().stream()
                .filter(c -> c.getId().equals(contactId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Emergency contact not found"));
        patient.getEmergencyContacts().remove(contact);
        patientRepository.save(patient);
    }

    @Transactional
    public AllergyResponse addAllergy(AllergyRequest request, UUID userId) {
        Patient patient = getOwnedPatient(userId);
        Allergy allergy = Allergy.builder()
                .patient(patient)
                .allergen(request.allergen())
                .notes(request.notes())
                .build();
        patient.getAllergies().add(allergy);
        patientRepository.flush();
        return new AllergyResponse(allergy.getId(), allergy.getAllergen(), allergy.getNotes());
    }

    @Transactional
    public PatientProfileResponse updateProfile(UpdatePatientProfileRequest request, UUID userId) {
        Patient patient = getOwnedPatient(userId);
        if (request.dateOfBirth() != null) {
            patient.setDateOfBirth(request.dateOfBirth());
        }
        if (request.phoneNumber() != null) {
            patient.setPhoneNumber(request.phoneNumber());
        }
        if (request.address() != null) {
            patient.setAddress(request.address());
        }
        patientRepository.save(patient);
        return toResponse(patient);
    }

    @Transactional
    public void removeAllergy(UUID allergyId, UUID userId) {
        Patient patient = getOwnedPatient(userId);
        Allergy allergy = patient.getAllergies().stream()
                .filter(a -> a.getId().equals(allergyId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Allergy not found"));
        patient.getAllergies().remove(allergy);
        patientRepository.save(patient);
    }

    private Patient getOwnedPatient(UUID userId) {
        return patientRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No patient profile found for this account"));
    }
}