-- Segunda base do mesmo Postgres, usada so por "mvn test"
-- (src/test/resources/application.properties).
--
-- O Postgres so corre os scripts desta pasta quando o volume de dados
-- esta vazio (primeira vez que o container arranca). Quem ja tem o volume
-- cria a base uma vez a mao: ver "Banco de dados" no README.
CREATE DATABASE export_tracking_test;
