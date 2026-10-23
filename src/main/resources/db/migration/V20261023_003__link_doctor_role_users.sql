UPDATE app_users app_user
SET doctor_id = doctor.id
FROM doctors_details doctor
WHERE app_user.doctor_id IS NULL
  AND app_user.organization_id = doctor.organization_id
  AND LOWER(app_user.email) = LOWER(doctor.doctor_email)
  AND EXISTS (
      SELECT 1
      FROM app_user_roles assignment
      JOIN app_roles role ON role.id = assignment.role_id
      WHERE assignment.user_id = app_user.id
        AND role.code = 'DOCTOR'
  );
