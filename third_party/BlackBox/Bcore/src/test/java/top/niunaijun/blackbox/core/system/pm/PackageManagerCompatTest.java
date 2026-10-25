package top.niunaijun.blackbox.core.system.pm;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class PackageManagerCompatTest {
    @Test public void assetPackLookupUsesExactSplitNameAndMatchingPath() {
        PackageInfo info = new PackageInfo();
        info.applicationInfo = new ApplicationInfo();
        info.applicationInfo.splitNames = new String[]{"config.arm64_v8a", "kr1_assets", "kr1_gc64"};
        info.applicationInfo.splitSourceDirs = new String[]{"/base/abi.apk", "/base/assets.apk", "/base/code.apk"};
        PackageManagerCompat.copySplitNames(info);
        assertArrayEquals(info.applicationInfo.splitNames, info.splitNames);
        assertNotSame(info.applicationInfo.splitNames, info.splitNames);
        int index = Arrays.binarySearch(info.splitNames, "kr1_gc64");
        assertEquals("/base/code.apk", info.applicationInfo.splitSourceDirs[index]);
        assertEquals("/base/assets.apk", info.applicationInfo.splitSourceDirs[
                Arrays.binarySearch(info.splitNames, "kr1_assets")]);
    }

    @Test public void missingOrMisalignedSplitMetadataIsNotInvented() {
        PackageInfo info = new PackageInfo();
        PackageManagerCompat.copySplitNames(info);
        assertNull(info.splitNames);
        info.applicationInfo = new ApplicationInfo();
        info.applicationInfo.splitNames = new String[]{"kr1_assets"};
        info.applicationInfo.splitSourceDirs = new String[]{"/one.apk", "/two.apk"};
        PackageManagerCompat.copySplitNames(info);
        assertNull(info.splitNames);
    }
}
