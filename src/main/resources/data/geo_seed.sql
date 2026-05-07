-- ============================================================
-- RentAPI — Seed geogràfic inicial
-- Executar a Supabase: SQL Editor > New Query > Run
-- ============================================================

-- ────────────────────────────────────────────────────────────
-- COMUNIDADES AUTÓNOMAS (17)
-- ────────────────────────────────────────────────────────────
INSERT INTO comunidades_autonomas (nombre, slug) VALUES
  ('Andalucía',             'andalucia'),
  ('Aragón',                'aragon'),
  ('Asturias',              'asturias'),
  ('Baleares',              'baleares'),
  ('Canarias',              'canarias'),
  ('Cantabria',             'cantabria'),
  ('Castilla-La Mancha',    'castilla-la-mancha'),
  ('Castilla y León',       'castilla-y-leon'),
  ('Cataluña',              'cataluna'),
  ('Comunidad Valenciana',  'comunidad-valenciana'),
  ('Extremadura',           'extremadura'),
  ('Galicia',               'galicia'),
  ('La Rioja',              'la-rioja'),
  ('Madrid',                'madrid'),
  ('Murcia',                'murcia'),
  ('Navarra',               'navarra'),
  ('País Vasco',            'pais-vasco')
ON CONFLICT (slug) DO NOTHING;

-- ────────────────────────────────────────────────────────────
-- PROVINCIAS (52)
-- ────────────────────────────────────────────────────────────
INSERT INTO provincias (nombre, slug, comunidad_id) VALUES
  -- Andalucía
  ('Almería',    'almeria',    (SELECT id FROM comunidades_autonomas WHERE slug = 'andalucia')),
  ('Cádiz',      'cadiz',      (SELECT id FROM comunidades_autonomas WHERE slug = 'andalucia')),
  ('Córdoba',    'cordoba',    (SELECT id FROM comunidades_autonomas WHERE slug = 'andalucia')),
  ('Granada',    'granada',    (SELECT id FROM comunidades_autonomas WHERE slug = 'andalucia')),
  ('Huelva',     'huelva',     (SELECT id FROM comunidades_autonomas WHERE slug = 'andalucia')),
  ('Jaén',       'jaen',       (SELECT id FROM comunidades_autonomas WHERE slug = 'andalucia')),
  ('Málaga',     'malaga',     (SELECT id FROM comunidades_autonomas WHERE slug = 'andalucia')),
  ('Sevilla',    'sevilla',    (SELECT id FROM comunidades_autonomas WHERE slug = 'andalucia')),
  -- Aragón
  ('Huesca',     'huesca',     (SELECT id FROM comunidades_autonomas WHERE slug = 'aragon')),
  ('Teruel',     'teruel',     (SELECT id FROM comunidades_autonomas WHERE slug = 'aragon')),
  ('Zaragoza',   'zaragoza',   (SELECT id FROM comunidades_autonomas WHERE slug = 'aragon')),
  -- Asturias
  ('Asturias',   'asturias-provincia', (SELECT id FROM comunidades_autonomas WHERE slug = 'asturias')),
  -- Baleares
  ('Baleares',   'baleares-provincia', (SELECT id FROM comunidades_autonomas WHERE slug = 'baleares')),
  -- Canarias
  ('Las Palmas', 'las-palmas', (SELECT id FROM comunidades_autonomas WHERE slug = 'canarias')),
  ('Santa Cruz de Tenerife', 'tenerife', (SELECT id FROM comunidades_autonomas WHERE slug = 'canarias')),
  -- Cantabria
  ('Cantabria',  'cantabria-provincia', (SELECT id FROM comunidades_autonomas WHERE slug = 'cantabria')),
  -- Castilla-La Mancha
  ('Albacete',   'albacete',   (SELECT id FROM comunidades_autonomas WHERE slug = 'castilla-la-mancha')),
  ('Ciudad Real','ciudad-real',(SELECT id FROM comunidades_autonomas WHERE slug = 'castilla-la-mancha')),
  ('Cuenca',     'cuenca',     (SELECT id FROM comunidades_autonomas WHERE slug = 'castilla-la-mancha')),
  ('Guadalajara','guadalajara',(SELECT id FROM comunidades_autonomas WHERE slug = 'castilla-la-mancha')),
  ('Toledo',     'toledo',     (SELECT id FROM comunidades_autonomas WHERE slug = 'castilla-la-mancha')),
  -- Castilla y León
  ('Ávila',      'avila',      (SELECT id FROM comunidades_autonomas WHERE slug = 'castilla-y-leon')),
  ('Burgos',     'burgos',     (SELECT id FROM comunidades_autonomas WHERE slug = 'castilla-y-leon')),
  ('León',       'leon',       (SELECT id FROM comunidades_autonomas WHERE slug = 'castilla-y-leon')),
  ('Palencia',   'palencia',   (SELECT id FROM comunidades_autonomas WHERE slug = 'castilla-y-leon')),
  ('Salamanca',  'salamanca',  (SELECT id FROM comunidades_autonomas WHERE slug = 'castilla-y-leon')),
  ('Segovia',    'segovia',    (SELECT id FROM comunidades_autonomas WHERE slug = 'castilla-y-leon')),
  ('Soria',      'soria',      (SELECT id FROM comunidades_autonomas WHERE slug = 'castilla-y-leon')),
  ('Valladolid', 'valladolid', (SELECT id FROM comunidades_autonomas WHERE slug = 'castilla-y-leon')),
  ('Zamora',     'zamora',     (SELECT id FROM comunidades_autonomas WHERE slug = 'castilla-y-leon')),
  -- Cataluña
  ('Barcelona',  'barcelona',  (SELECT id FROM comunidades_autonomas WHERE slug = 'cataluna')),
  ('Girona',     'girona',     (SELECT id FROM comunidades_autonomas WHERE slug = 'cataluna')),
  ('Lleida',     'lleida',     (SELECT id FROM comunidades_autonomas WHERE slug = 'cataluna')),
  ('Tarragona',  'tarragona',  (SELECT id FROM comunidades_autonomas WHERE slug = 'cataluna')),
  -- Comunidad Valenciana
  ('Alicante',   'alicante',   (SELECT id FROM comunidades_autonomas WHERE slug = 'comunidad-valenciana')),
  ('Castellón',  'castellon',  (SELECT id FROM comunidades_autonomas WHERE slug = 'comunidad-valenciana')),
  ('Valencia',   'valencia',   (SELECT id FROM comunidades_autonomas WHERE slug = 'comunidad-valenciana')),
  -- Extremadura
  ('Badajoz',    'badajoz',    (SELECT id FROM comunidades_autonomas WHERE slug = 'extremadura')),
  ('Cáceres',    'caceres',    (SELECT id FROM comunidades_autonomas WHERE slug = 'extremadura')),
  -- Galicia
  ('A Coruña',   'a-coruna',   (SELECT id FROM comunidades_autonomas WHERE slug = 'galicia')),
  ('Lugo',       'lugo',       (SELECT id FROM comunidades_autonomas WHERE slug = 'galicia')),
  ('Ourense',    'ourense',    (SELECT id FROM comunidades_autonomas WHERE slug = 'galicia')),
  ('Pontevedra', 'pontevedra', (SELECT id FROM comunidades_autonomas WHERE slug = 'galicia')),
  -- La Rioja
  ('La Rioja',   'la-rioja-provincia', (SELECT id FROM comunidades_autonomas WHERE slug = 'la-rioja')),
  -- Madrid
  ('Madrid',     'madrid-provincia',   (SELECT id FROM comunidades_autonomas WHERE slug = 'madrid')),
  -- Murcia
  ('Murcia',     'murcia-provincia',   (SELECT id FROM comunidades_autonomas WHERE slug = 'murcia')),
  -- Navarra
  ('Navarra',    'navarra-provincia',  (SELECT id FROM comunidades_autonomas WHERE slug = 'navarra')),
  -- País Vasco
  ('Álava',      'alava',      (SELECT id FROM comunidades_autonomas WHERE slug = 'pais-vasco')),
  ('Guipúzcoa',  'guipuzcoa',  (SELECT id FROM comunidades_autonomas WHERE slug = 'pais-vasco')),
  ('Vizcaya',    'vizcaya',    (SELECT id FROM comunidades_autonomas WHERE slug = 'pais-vasco'))
ON CONFLICT (slug) DO NOTHING;

-- ────────────────────────────────────────────────────────────
-- CIUDADES PRINCIPALES
-- ────────────────────────────────────────────────────────────
INSERT INTO ciudades (nombre, slug, provincia_id, latitud, longitud) VALUES
  ('Barcelona',  'barcelona',  (SELECT id FROM provincias WHERE slug = 'barcelona'),  41.385064,  2.173403),
  ('Madrid',     'madrid',     (SELECT id FROM provincias WHERE slug = 'madrid-provincia'), 40.416775, -3.703790),
  ('Valencia',   'valencia',   (SELECT id FROM provincias WHERE slug = 'valencia'),   39.469907, -0.376288),
  ('Sevilla',    'sevilla',    (SELECT id FROM provincias WHERE slug = 'sevilla'),    37.389092, -5.984459),
  ('Zaragoza',   'zaragoza',   (SELECT id FROM provincias WHERE slug = 'zaragoza'),  41.650825, -0.887861),
  ('Málaga',     'malaga',     (SELECT id FROM provincias WHERE slug = 'malaga'),    36.721261, -4.421265),
  ('Bilbao',     'bilbao',     (SELECT id FROM provincias WHERE slug = 'vizcaya'),   43.262985, -2.934985),
  ('Alicante',   'alicante',   (SELECT id FROM provincias WHERE slug = 'alicante'),  38.345996, -0.490686),
  ('Granada',    'granada',    (SELECT id FROM provincias WHERE slug = 'granada'),   37.177336, -3.598557),
  ('Murcia',     'murcia',     (SELECT id FROM provincias WHERE slug = 'murcia-provincia'), 37.983810, -1.129473),
  ('Palma',      'palma',      (SELECT id FROM provincias WHERE slug = 'baleares-provincia'), 39.569600, 2.650160),
  ('Las Palmas de Gran Canaria', 'las-palmas-de-gran-canaria', (SELECT id FROM provincias WHERE slug = 'las-palmas'), 28.099160, -15.413611),
  ('Valladolid', 'valladolid', (SELECT id FROM provincias WHERE slug = 'valladolid'), 41.652251, -4.724532),
  ('Córdoba',    'cordoba',    (SELECT id FROM provincias WHERE slug = 'cordoba'),   37.888175, -4.779152),
  ('Banyoles',   'banyoles',   (SELECT id FROM provincias WHERE slug = 'girona'),    42.116667,  2.766667),
  ('Girona',     'girona',     (SELECT id FROM provincias WHERE slug = 'girona'),    41.979440,  2.821549),
  ('Tarragona',  'tarragona',  (SELECT id FROM provincias WHERE slug = 'tarragona'), 41.118300,  1.245900),
  ('Lleida',     'lleida',     (SELECT id FROM provincias WHERE slug = 'lleida'),    41.614700,  0.624170),
  ('San Sebastián', 'san-sebastian', (SELECT id FROM provincias WHERE slug = 'guipuzcoa'), 43.318334, -1.981250),
  ('Santander',  'santander',  (SELECT id FROM provincias WHERE slug = 'cantabria-provincia'), 43.462306, -3.809980),
  ('Oviedo',     'oviedo',     (SELECT id FROM provincias WHERE slug = 'asturias-provincia'), 43.361914, -5.849388),
  ('Pamplona',   'pamplona',   (SELECT id FROM provincias WHERE slug = 'navarra-provincia'), 42.812526, -1.645770),
  ('Salamanca',  'salamanca',  (SELECT id FROM provincias WHERE slug = 'salamanca'), 40.970104, -5.663540),
  ('A Coruña',   'a-coruna',   (SELECT id FROM provincias WHERE slug = 'a-coruna'),  43.362343, -8.411540),
  ('Vigo',       'vigo',       (SELECT id FROM provincias WHERE slug = 'pontevedra'), 42.231880, -8.712447)
ON CONFLICT (slug) DO NOTHING;
