package com.doctor.clinic.DoctorClinic.serviceImpl;

import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.doctor.clinic.DoctorClinic.entity.Doctor;
import com.doctor.clinic.DoctorClinic.entity.Organization;
import com.doctor.clinic.DoctorClinic.repo.DoctorRepo;
import com.doctor.clinic.DoctorClinic.repo.OrganizationRepo;
import com.doctor.clinic.DoctorClinic.request.DoctorRegisterRequest;
import com.doctor.clinic.DoctorClinic.request.WhatsAppBusinessActivateRequest;
import com.doctor.clinic.DoctorClinic.response.DoctorResponse;
import com.doctor.clinic.DoctorClinic.service.DoctorService;

import jakarta.validation.Valid;

@Service
public class DoctorServiceImpl implements DoctorService {

	@Autowired
	private DoctorRepo doctorRepo;

	@Autowired
	private OrganizationRepo organizationRepo;

	private final WebClient webClient = WebClient.builder().build();

	@Value("${facebook.app.id}")
	private String appId;

	@Value("${facebook.app.secret}")
	private String appSecret;

	@Value("${facebook.redirect.uri:}")
	private String redirectUri;

	@Override
	public String addNewDoctor(DoctorRegisterRequest req) {
		Organization organization = organizationRepo.findByOrganizationName(req.getOrganizationName()).orElseThrow(
				() -> new RuntimeException("Organization not found with name: " + req.getOrganizationName()));

		Doctor doctor = Doctor.builder().firstName(req.getFirstName()).lastName(req.getLastName())
				.registrationNumber(req.getRegistrationNumber()).qualification(req.getQualification())
				.specialization(req.getSpecialization()).experienceYears(req.getExperienceYears())
				.phoneNumber(req.getPhoneNumber()).email(req.getEmail()).consultationFee(req.getConsultationFee())
				.consultationHours(req.getConsultationHours()).organization(organization).status("ACTIVE").build();

		Doctor savedDoctor = doctorRepo.save(doctor);

		organization.incrementDoctorCount();
		organizationRepo.save(organization);

		return "Doctor added successfully with ID: " + savedDoctor.getId();
	}

	@Override
	public Map<String, Object> activateNumber(WhatsAppBusinessActivateRequest request) {

		String authorizationCode = request.getAuthorizationCode();
		Long doctorId = request.getDoctorId();

		System.out.println("Activating WhatsApp for doctor ID: {}" + doctorId);

		// Step 1: Exchange authorization code for access token
		String tokenUrl = "https://graph.facebook.com/v22.0/oauth/access_token" + "?client_id=" + appId
				+ "&client_secret=" + appSecret + "&code=" + authorizationCode + "&redirect_uri=" + redirectUri;

		Map<String, Object> tokenResponse = webClient.get().uri(tokenUrl).retrieve().bodyToMono(Map.class).block();

		if (tokenResponse == null || tokenResponse.containsKey("error")) {
			System.out.println("Failed to get access token: {}" + tokenResponse);
			throw new RuntimeException("Failed to get access token");
		}

		String accessToken = (String) tokenResponse.get("access_token");
		String userId = String.valueOf(tokenResponse.get("user_id"));

		System.out.println("Got access token for user: {}" + userId);

		// Step 2: Get phone numbers for this user
		String phoneUrl = "https://graph.facebook.com/v22.0/" + userId + "/phone_numbers";

		Map<String, Object> phoneResponse = webClient.get().uri(phoneUrl)
				.header("Authorization", "Bearer " + accessToken).retrieve().bodyToMono(Map.class).block();

		if (phoneResponse == null || !phoneResponse.containsKey("data")) {
			throw new RuntimeException("No phone numbers found");
		}

		var phoneNumbers = (java.util.List<Map<String, Object>>) phoneResponse.get("data");
		if (phoneNumbers.isEmpty()) {
			throw new RuntimeException("No phone numbers found for this user");
		}

		String phoneNumberId = String.valueOf(phoneNumbers.get(0).get("id"));
		String phoneNumber = (String) phoneNumbers.get(0).get("verified_name");

		System.out.println("Phone number found: {} with ID: {}" + phoneNumber + "  " + phoneNumberId);

		// Step 3: Save to database
		Doctor doctor = doctorRepo.findById(doctorId).orElseThrow(() -> new RuntimeException("Doctor not found"));

		doctor.setWhatsappPhoneNumberId(phoneNumberId);
		doctor.setWhatsappAccessToken(accessToken);
		doctor.setWhatsappNumber(phoneNumber);
		doctor.setWhatsappActivated(true);
		doctorRepo.save(doctor);

		System.out.println("WhatsApp activated successfully for doctor: {}" + doctor.getFullName());

		// Step 4: Return response
		return Map.of("success", true, "message", "WhatsApp number activated successfully", "phoneNumberId",
				phoneNumberId, "phoneNumber", phoneNumber, "doctorId", doctorId);
	}

	@Override
	public DoctorResponse getDoctorDetailsByID(Long doctorId) {

	    Doctor doctor = doctorRepo.findById(doctorId)
	            .orElseThrow(() -> new RuntimeException("Doctor not found"));

	    return DoctorResponse.builder()
	            .id(doctor.getId())
	            .firstName(doctor.getFirstName())
	            .lastName(doctor.getLastName())
	            .registrationNumber(doctor.getRegistrationNumber())
	            .qualification(doctor.getQualification())
	            .specialization(doctor.getSpecialization())
	            .experienceYears(doctor.getExperienceYears())
	            .phoneNumber(doctor.getPhoneNumber())
	            .email(doctor.getEmail())
	            .consultationFee(doctor.getConsultationFee())
	            .consultationHours(doctor.getConsultationHours())
	            .status(doctor.getStatus())
	            .defaultSlotDurationMinutes(doctor.getDefaultSlotDurationMinutes())
	            .whatsappPhoneNumberId(doctor.getWhatsappPhoneNumberId())
	            .whatsappNumber(doctor.getWhatsappNumber())
	            .whatsappActivated(doctor.isWhatsappActivated())
	            .createdAt(doctor.getCreatedAt())
	            .updatedAt(doctor.getUpdatedAt())
	            .organizationId(doctor.getOrganization().getId())
	            .organizationName(doctor.getOrganization().getOrganizationName())
	            .build();
	}
}
