-- V9 (Depot only): give pre-V8 trips and consignments a delivery point.
--
-- Existing test data was created before delivery points existed, so its
-- delivery_point_id is NULL. Point it at Maseru. Run this ONLY after
-- V8 is applied everywhere AND extend-replication-v8.sh has run AND
-- delivery_point shows 10 rows at all four sites -- the UPDATEs below
-- replicate to the other sites, which must already hold the column.
--
-- Run at Depot only. Non-depot sites cannot write master tables, and the
-- replicated UPDATEs reach them on their own.

UPDATE trip_master
   SET delivery_point_id = (SELECT delivery_point_id FROM delivery_point WHERE name = 'Maseru')
 WHERE delivery_point_id IS NULL;

UPDATE consignment
   SET delivery_point_id = (SELECT delivery_point_id FROM delivery_point WHERE name = 'Maseru')
 WHERE delivery_point_id IS NULL;
