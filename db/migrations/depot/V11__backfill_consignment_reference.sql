-- V11 (Depot only): give pre-V10 consignments a reference number.
--
-- Run ONLY after V10 is applied at all four sites -- these UPDATEs replicate
-- outward, and every site must already hold the column. Run at Depot only.
--
-- Existing rows get the next numbers from the sequence, in a stable order,
-- so they will not collide with the numbers the application issues later.
-- All other new details are left empty for these rows (shown as blank in
-- the UI).

UPDATE consignment c
   SET reference_number = 'CON-' || to_char(now(), 'YYYY') || '-' || lpad(n.seq::text, 4, '0')
  FROM (
        SELECT consignment_id, nextval('consignment_reference_seq') AS seq
          FROM (SELECT consignment_id FROM consignment
                 WHERE reference_number IS NULL
                 ORDER BY consignment_id) ordered
       ) n
 WHERE c.consignment_id = n.consignment_id;
