ALTER TABLE public.places
    ALTER COLUMN business_hours DROP NOT NULL,
    ALTER COLUMN description DROP NOT NULL;
