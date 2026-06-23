UPDATE shop_staff SET role_in_shop = 'OWNER' WHERE role_in_shop = 'Owner';
UPDATE shop_staff SET role_in_shop = 'STAFF' WHERE role_in_shop NOT IN ('OWNER');