package com.doctor.clinic.DoctorClinic.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.doctor.clinic.DoctorClinic.model.OrganizationType;
import com.doctor.clinic.DoctorClinic.model.SubscriptionStatus;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "organizations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Organization {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotBlank(message = "Full name is required")
	@Column(name = "owner_full_name", nullable = false)
	private String ownerFullName;

	@NotBlank(message = "Mobile number is required")
	@Pattern(regexp = "^[6-9]\\d{9}$", message = "Invalid Indian mobile number")
	@Column(name = "owner_mobile", nullable = false, unique = true)
	private String ownerMobile;

	@NotBlank(message = "Email is required")
	@Email(message = "Invalid email format")
	@Column(name = "owner_email", nullable = false, unique = true)
	private String ownerEmail;

	@NotBlank(message = "Organization name is required")
	@Column(name = "organization_name", nullable = false)
	private String organizationName;

	@Enumerated(EnumType.STRING)
	@Column(name = "organization_type", nullable = false)
	private OrganizationType organizationType;

	@NotBlank(message = "Password is required")
	@Column(name = "password_hash", nullable = false)
	private String passwordHash; // Store encoded password, never plain text

	@Column(name = "max_doctors", nullable = false)
	private Integer maxDoctors; // Will be set based on organization type

	@Column(name = "doctors_count", nullable = false)
	@Builder.Default
	private Integer doctorsCount = 0;

	@Enumerated(EnumType.STRING)
	@Column(name = "subscription_status", nullable = false)
	@Builder.Default
	private SubscriptionStatus subscriptionStatus = SubscriptionStatus.TRIAL;

	@Column(name = "trial_ends_at")
	private LocalDate trialEndsAt;

	@Column(name = "is_active")
	@Builder.Default
	private Boolean isActive = false;

	@Column(name = "email_verified")
	@Builder.Default
	private Boolean emailVerified = false;

	@Column(name = "mobile_verified")
	@Builder.Default
	private Boolean mobileVerified = false;

	@Column(name = "last_login_at")
	private LocalDateTime lastLoginAt;

	@Column(name = "created_at", updatable = false)
	@Builder.Default
	private LocalDateTime createdAt = LocalDateTime.now();

	@Column(name = "updated_at")
	@Builder.Default
	private LocalDateTime updatedAt = LocalDateTime.now();

	// Relationships
	@OneToMany(mappedBy = "organization", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	@Builder.Default
	private List<Doctor> doctors = new ArrayList<>();

	@OneToMany(mappedBy = "organization", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	@Builder.Default
	private List<Subscription> subscriptions = new ArrayList<>();

	// Helper methods
	public boolean canAddDoctor() {
		return doctorsCount < maxDoctors;
	}

	public boolean isTrialExpired() {
		return trialEndsAt != null && LocalDate.now().isAfter(trialEndsAt);
	}

	public void incrementDoctorCount() {
		this.doctorsCount++;
		this.updatedAt = LocalDateTime.now();
	}

	public void decrementDoctorCount() {
		if (this.doctorsCount > 0) {
			this.doctorsCount--;
			this.updatedAt = LocalDateTime.now();
		}
	}

	@PreUpdate
	protected void onUpdate() {
		this.updatedAt = LocalDateTime.now();
	}
}