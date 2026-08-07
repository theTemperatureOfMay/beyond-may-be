ALTER TABLE public.visits
    ADD COLUMN place_id bigint;

UPDATE public.visits AS visit
SET place_id = course_place.place_id
FROM public.course_places AS course_place
WHERE visit.course_place_id = course_place.course_place_id;

ALTER TABLE public.visits
    ALTER COLUMN place_id SET NOT NULL,
    ALTER COLUMN course_place_id DROP NOT NULL,
    DROP CONSTRAINT uk_visits_participant_course_place,
    ADD CONSTRAINT uk_visits_participant_place UNIQUE (participant_id, place_id);
