package com.doctor.clinic.DoctorClinic.serviceImpl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.doctor.clinic.DoctorClinic.entity.Doctor;
import com.doctor.clinic.DoctorClinic.entity.Organization;
import com.doctor.clinic.DoctorClinic.model.SubscriptionStatus;
import com.doctor.clinic.DoctorClinic.repo.AppointmentRepo;
import com.doctor.clinic.DoctorClinic.repo.DoctorRepo;
import com.doctor.clinic.DoctorClinic.repo.DoctorSlotRepo;
import com.doctor.clinic.DoctorClinic.repo.OrganizationRepo;
import com.doctor.clinic.DoctorClinic.request.OrganizationRegistrationRequest;
import com.doctor.clinic.DoctorClinic.response.DoctorListInfo;
import com.doctor.clinic.DoctorClinic.response.OrganizationDashboardResponse;
import com.doctor.clinic.DoctorClinic.response.OrganizationSummary;
import com.doctor.clinic.DoctorClinic.service.OrganizationService;



@Service
public class OrganizationServiceImpl implements OrganizationService {

	@Autowired
	private OrganizationRepo organizationRepo;

	@Autowired
	private DoctorRepo doctorRepo;

	@Autowired
	private AppointmentRepo appointmentRepo;

	@Autowired
	private DoctorSlotRepo doctorSlotRepo;

	@Override
	public String registerOrganization(OrganizationRegistrationRequest req) {
		// Create and populate organization
		Organization org = Organization.builder().ownerFullName(req.getFullName()).ownerMobile(req.getMobileNumber())
				.ownerEmail(req.getEmail()).organizationName(req.getOrganizationName())
				.organizationType(req.getOrganizationType())
				.maxDoctors(req.getOrganizationType().getDefaultMaxDoctors()).doctorsCount(0)
				.subscriptionStatus(SubscriptionStatus.TRIAL)
				.trialEndsAt(LocalDateTime.now().toLocalDate().plusMonths(3)).lastLoginAt(LocalDateTime.now())
				.passwordHash(req.getPassword()).isActive(true) // You might want to set this based on email
																// verification
				.emailVerified(false).mobileVerified(false).build();

		// Save the organization
		Organization savedOrg = organizationRepo.save(org);

		return "Organization registered successfully with ID: " + savedOrg.getId();
	}

	@Override
	public OrganizationDashboardResponse getOrganizationDashboard(Long organizationId) {

		Organization org = organizationRepo.findById(organizationId)
				.orElseThrow(() -> new RuntimeException("Organization not found"));

		LocalDate today = LocalDate.now();

		// Get all doctors in this organization
		List<Doctor> doctors = doctorRepo.findByOrganizationId(organizationId);

		// Build doctor list
		List<DoctorListInfo> doctorList = doctors.stream().map(doctor -> buildDoctorInfo(doctor, today))
				.collect(Collectors.toList());

		// Calculate summary
		int totalDoctors = doctors.size();
		int activeDoctors = (int) doctors.stream().filter(d -> "ACTIVE".equals(d.getStatus())).count();
		int totalAppointmentsToday = doctorList.stream().mapToInt(DoctorListInfo::getTodayAppointments).sum();
		BigDecimal todayRevenue = doctorList.stream().map(DoctorListInfo::getTodayEarnings).reduce(BigDecimal.ZERO,
				BigDecimal::add);
		int availableSlotsToday = doctorList.stream().mapToInt(DoctorListInfo::getAvailableSlots).sum();

		OrganizationSummary summary = OrganizationSummary.builder().totalDoctors(totalDoctors)
				.activeDoctors(activeDoctors).totalAppointmentsToday(totalAppointmentsToday).todayRevenue(todayRevenue)
				.availableSlotsToday(availableSlotsToday).build();

		return OrganizationDashboardResponse.builder().organizationId(org.getId())
				.organizationName(org.getOrganizationName()).organizationType(org.getOrganizationType().name())
				.currentDate(today).summary(summary).doctors(doctorList).build();
	}

	private DoctorListInfo buildDoctorInfo(Doctor doctor, LocalDate date) {

		int todayAppointments = appointmentRepo.countByDoctorIdAndAppointmentDate(doctor.getId(), date);
		BigDecimal todayEarnings = appointmentRepo.sumEarningsByDoctorIdAndDate(doctor.getId(), date);
		int availableSlots = doctorSlotRepo.countByDoctorIdAndSlotDate(doctor.getId(), date);
		//int availableSlots = doctorSlotRepo.countByDoctorIdAndSlotDateAndStatus(doctor.getId(), date, "AVAILABLE");

		return DoctorListInfo.builder().doctorId(doctor.getId()).doctorName(doctor.getFullName())
				.specialization(doctor.getSpecialization()).qualification(doctor.getQualification())
				.experienceYears(doctor.getExperienceYears()).phoneNumber(doctor.getPhoneNumber())
				.email(doctor.getEmail()).status(doctor.getStatus()).todayAppointments(todayAppointments)
				.todayEarnings(todayEarnings != null ? todayEarnings : BigDecimal.ZERO).availableSlots(availableSlots)
				.build();
	}
}
