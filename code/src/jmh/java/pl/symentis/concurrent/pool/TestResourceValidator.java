// Copyright © 2025 Symentis.pl (Jarosław Pałka)
package pl.symentis.concurrent.pool;

class TestResourceValidator implements ObjectValidator<TestResource> {
    @Override
    public boolean isValid(TestResource resource) {
        return resource != null && resource.valid;
    }
}
