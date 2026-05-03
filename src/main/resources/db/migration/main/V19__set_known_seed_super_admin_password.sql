UPDATE users
SET password = '$2a$10$d.DHuG9JyfQGT6vfL/OaL.XvZh.7zBvcaM9esW.ftNNhV.ti6XPdi',
    updated_at = now()
WHERE lower(email) = lower('superadmin@m.com');
