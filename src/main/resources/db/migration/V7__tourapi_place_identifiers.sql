ALTER TABLE public.places
    ADD COLUMN tour_content_id bigint,
    ADD COLUMN tour_content_type_id integer,
    ADD CONSTRAINT uk_places_tour_content_id UNIQUE (tour_content_id);
