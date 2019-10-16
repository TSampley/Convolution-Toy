package com.taushsampley.convolution;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import static junit.framework.Assert.assertEquals;

/**
 * @author taushsampley
 */
@RunWith(AndroidJUnit4.class)
public class GLRendererTest {

    private static final String SMALL_TEXT = "This is just a small example";
    private static final String BIG_TEXT =
            "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Cras nec dictum mauris, et pharetra leo. Nulla porttitor vel ligula scelerisque molestie. Nunc a feugiat velit, luctus efficitur ex. Donec vehicula euismod nisl, eu finibus lorem fermentum facilisis. Nulla eu volutpat odio. Vivamus sed eleifend sapien. Phasellus vel commodo velit.\n" +
            "\n" +
            "Proin at massa dui. Curabitur ultrices dui sed quam rutrum pellentesque. Sed porttitor, magna eu ultricies semper, mi tellus convallis lacus, vel suscipit diam neque quis nunc. Cras eget condimentum metus, at bibendum est. Vestibulum id luctus elit. Praesent vel orci auctor, mollis nunc gravida, sollicitudin leo. Etiam aliquet, orci vitae dignissim scelerisque, magna sem feugiat elit, in porta neque nibh eget odio. In tincidunt tempus nunc, sit amet pulvinar velit. Sed ac pretium dui. Donec sollicitudin luctus metus vel convallis. Cras sit amet lectus diam. Mauris dui risus, ullamcorper id consectetur eu, vulputate venenatis sem. Ut eu ex eget risus ultricies imperdiet vel eu massa. Fusce libero libero, pulvinar sit amet augue sed, suscipit interdum elit.\n" +
            "\n" +
            "Phasellus eget pulvinar nisi. Sed sed consequat erat, id dapibus augue. Interdum et malesuada fames ac ante ipsum primis in faucibus. Lorem ipsum dolor sit amet, consectetur adipiscing elit. Nullam lorem leo, vestibulum id nulla vel, placerat mollis nunc. Pellentesque luctus at dolor a aliquam. Vestibulum varius bibendum aliquam. Nunc euismod, turpis in semper condimentum, magna eros bibendum augue, non semper est erat eget purus. Quisque ullamcorper, nisl vitae posuere porttitor, erat erat vehicula dolor, sit amet iaculis nibh velit et lorem. Donec luctus lorem sodales, fringilla ex quis, sodales urna. Ut scelerisque porta diam vitae semper. Mauris sollicitudin augue purus, et sodales est pharetra congue. Suspendisse malesuada, dui eget venenatis malesuada, justo mauris mollis enim, vitae aliquam sem lorem eu ligula. Mauris eget justo at lacus blandit ultricies eu ac enim. Aliquam erat volutpat.\n" +
            "\n" +
            "Sed sit amet malesuada sapien. Suspendisse placerat est ut condimentum accumsan. Nulla nec metus sit amet nunc condimentum laoreet. Vestibulum et dolor at velit malesuada faucibus at nec sem. Vivamus a leo quis metus consectetur vulputate. In hac habitasse platea dictumst. In facilisis ipsum vitae ipsum cursus, eget gravida turpis facilisis. Suspendisse eu orci vestibulum, finibus erat semper, consectetur est. Suspendisse nec tortor eleifend, semper ante ac, dictum mi. Phasellus sodales blandit ante nec mattis. Pellentesque sed feugiat sem, ut mattis mi.\n" +
            "\n" +
            "In tincidunt odio eget nunc luctus aliquet. Curabitur urna dui, lobortis eget porttitor auctor, hendrerit sed dolor. Nullam accumsan maximus lacinia. Donec lorem diam, scelerisque ut elit eget, gravida interdum enim. Maecenas erat risus, hendrerit nec elementum eu, sagittis eget erat. Morbi pulvinar purus non nulla congue, vehicula accumsan risus sollicitudin. Cras sed tortor dapibus, scelerisque turpis ut, lobortis dolor. Morbi eu urna nec arcu feugiat maximus.\n";
    private static final String INTERNATIONAL_CHARS = "";

    private Context appContext;

    @Before
    public void setUp() throws Exception {
        appContext = InstrumentationRegistry.getTargetContext();
    }

    @Test
    public void readShaderFile() {
        String smallOutput = MainActivity.readRawResource(appContext, R.raw.small_text);
        String bigOutput = MainActivity.readRawResource(appContext, R.raw.big_text);
        String international = MainActivity.readRawResource(appContext, R.raw.international_chars);

        assertEquals(SMALL_TEXT, smallOutput);
        assertEquals(BIG_TEXT, bigOutput);
        assertEquals(INTERNATIONAL_CHARS, international);
    }
}