-- ==============================================================================
-- PANDALFINDER v2.0.0 — SUPABASE STORAGE & DATABASE SECURITY SETUP
-- ==============================================================================
-- This script configures Row Level Security (RLS) for Supabase Storage and the
-- public.admin_users table to enforce strict ownership and admin authorization.
--
-- Architecture:
-- - Supabase Third-Party Auth verifies Firebase ID tokens (JWT).
-- - auth.jwt() ->> 'sub' contains the Firebase Authenticated UID.
-- - Storage bucket: 'pandal-photos' (5MB limit, JPEG/PNG/WebP).
-- ==============================================================================

-- 1. Create the Admin Users allowlist table
CREATE TABLE IF NOT EXISTS public.admin_users (
    firebase_uid TEXT PRIMARY KEY,
    role TEXT NOT NULL DEFAULT 'admin',
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Enable RLS on admin_users table
ALTER TABLE public.admin_users ENABLE ROW LEVEL SECURITY;

-- Deny all client writes on admin_users table (can only be updated via Supabase Dashboard / SQL editor)
DROP POLICY IF EXISTS "Deny client writes on admin_users" ON public.admin_users;
CREATE POLICY "Deny client writes on admin_users"
ON public.admin_users
FOR ALL
TO anon, authenticated
USING (false);

-- 2. Helper function to check if the current user is an authorized admin
CREATE OR REPLACE FUNCTION public.is_admin()
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.admin_users
    WHERE firebase_uid = (auth.jwt() ->> 'sub')
      AND role = 'admin'
      AND enabled = true
  );
$$;

-- 3. Storage Bucket Configuration
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'pandal-photos',
    'pandal-photos',
    true,
    5242880, -- 5 MB limit
    ARRAY['image/jpeg', 'image/png', 'image/webp']
)
ON CONFLICT (id) DO UPDATE SET
    public = true,
    file_size_limit = 5242880,
    allowed_mime_types = ARRAY['image/jpeg', 'image/png', 'image/webp'];

-- Enable RLS on storage.objects
ALTER TABLE storage.objects ENABLE ROW LEVEL SECURITY;

-- 4. Storage Policies

-- SELECT: Public read access for community pandal photos
DROP POLICY IF EXISTS "Public Read on Pandal Photos" ON storage.objects;
CREATE POLICY "Public Read on Pandal Photos"
ON storage.objects
FOR SELECT
USING (bucket_id = 'pandal-photos');

-- INSERT: Authenticated users can ONLY insert into their own UID folder, or Admin
-- Object path format: {firebaseUid}/{pandalId}/{photoId}.jpg
DROP POLICY IF EXISTS "Authenticated Insert on Own Folder" ON storage.objects;
CREATE POLICY "Authenticated Insert on Own Folder"
ON storage.objects
FOR INSERT
WITH CHECK (
    bucket_id = 'pandal-photos'
    AND (
        (storage.foldername(name))[1] = (auth.jwt() ->> 'sub')
        OR public.is_admin()
    )
);

-- UPDATE: Owner or Admin only
DROP POLICY IF EXISTS "Owner or Admin Update on Photos" ON storage.objects;
CREATE POLICY "Owner or Admin Update on Photos"
ON storage.objects
FOR UPDATE
USING (
    bucket_id = 'pandal-photos'
    AND (
        (storage.foldername(name))[1] = (auth.jwt() ->> 'sub')
        OR public.is_admin()
    )
);

-- DELETE: Owner or Admin only
DROP POLICY IF EXISTS "Owner or Admin Delete on Photos" ON storage.objects;
CREATE POLICY "Owner or Admin Delete on Photos"
ON storage.objects
FOR DELETE
USING (
    bucket_id = 'pandal-photos'
    AND (
        (storage.foldername(name))[1] = (auth.jwt() ->> 'sub')
        OR public.is_admin()
    )
);

-- ==============================================================================
-- 5. Bootstrap Initial Admin (Replace placeholder with real Firebase Admin UID)
-- ==============================================================================
-- INSERT INTO public.admin_users (firebase_uid, role, enabled)
-- VALUES ('YOUR_ADMIN_FIREBASE_UID', 'admin', true)
-- ON CONFLICT (firebase_uid) DO UPDATE SET enabled = true, role = 'admin';
