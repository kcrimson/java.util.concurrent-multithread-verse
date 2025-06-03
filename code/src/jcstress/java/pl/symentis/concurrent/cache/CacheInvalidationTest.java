package pl.symentis.concurrent.cache;

import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.Expect;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.ZZ_Result;

@JCStressTest
@Outcome(id = "true, true", expect = Expect.FORBIDDEN, desc = "Both keys have expected value")
@Outcome(id = "true, false", expect = Expect.ACCEPTABLE, desc = "First key has expected value, second key is invalid (null)")
@Outcome(id = "false, true", expect = Expect.ACCEPTABLE, desc = "Second key has expected value, first key is invalid (null)")
@Outcome(id = "false, false", expect = Expect.FORBIDDEN, desc = "Both keys have unexpected value")
@State
public class CacheInvalidationTest {

    private final Cache<String, String> cache = new Cache<>(1, key -> null);

    @Actor
    public void putKey0() {
        cache.put("key0", "value0");
    }

    @Actor
    public void putKey1() {
        cache.put("key1", "value1");
    }

    @Arbiter
    public void arbiter(ZZ_Result result) {
        result.r1 = "value0".equals(cache.get("key0"));
        result.r2 = "value1".equals(cache.get("key1"));
    }

}
