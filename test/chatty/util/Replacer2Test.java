
package chatty.util;

import org.junit.Test;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 *
 * @author tduva
 */
public class Replacer2Test {

    @Test
    public void test() {
        //--------------------------
        // General tests
        //--------------------------
        test(new String[]{
            "a а", // Cyrillic a
            "o ()"
        }, "hаt yell()w",
                "yell", "yell",
                "yellow", "yell()w",
                "hat", "hаt"
        );
        
        test(new String[]{
            "a а",
            "o ()"
        }, "h𝒜t",
                "hat", null
        );
        
        test(new String[]{
            "a а 𝒜",
            "o ()"
        }, "h𝒜t",
                "hat", "h𝒜t",
                "h", "h",
                "t", "t"
        );
        
        test(new String[]{
            "a 𝒜 @",
            "t test"
        }, "Test h@t",
                "t", "Test",
                "hat", "h@t",
                "t$", "t"
        );
        
        test(new String[]{
            "a # a"
        }, "äàåÅ",
                "a", "ä",
                "aa", "äà",
                "aaa", "äàå",
                "aaaa", "äàåÅ"
        );
        
        test(new String[]{
            "a @"
        }, "@",
                "@", null
        );
        
        test(new String[]{
            "a 𝒜 @",
            "t test",
            "b"
        }, "testi c𝒜t h@t test c𝒜r",
                " t", " test",
                "car", "c𝒜r",
                ".{5}$", "test c𝒜r",
                ".{6}$", " test c𝒜r",
                "hat", "h@t",
                "hat t", "h@t test",
                "hat t c", "h@t test c",
                "\\b\\w{3}\\b", "c𝒜t",
                "r", "r"
        );
        
        test(new String[]{
            "a # a 𝝰 𝙰 ａ 𝗮 𝐴 ᗅ 𝑨 𝚨 𝕬 𝓪 𝞪 𝖠 ɑ 𝔞 𝘢 𝛢 𝙖 𝝖 𝒜 𝜜 𝐚 𖽀 𝓐 𝞐 𝑎 𝗔 𝕒 𝘈 𝖆 Ꭺ 𝚊 ꓮ а 𝐀 α 𝔄 𝒂 𝛂 𝔸 ⍺ 𝒶 𝜶 𐊠 𝛼 𝘼 𝖺",
            "b # b 𝗯 ｂ 𝕭 ƅ 𝙱 ь 𝓫 Ꮟ 𝑩 𝚩 ꓐ 𝔟 𝜝 𝘣 𝛣 𝖡 𝙗 𝝗 𐊂 𐌁 𝗕 𝐛 𝑏 𝕓 𝓑 𝞑 𝖇 𝔅 ℬ 𝚋 ᖯ 𝘉 ᑲ β в 𝘽 Ꞵ Ᏼ 𝒃 𝐁 ᗷ 𝒷 𐊡 𝐵 𝖻 𝔹",
            "c # c 𝗰 с ℂ 𝕮 ｃ ᴄ 𝙲 𐐽 𝓬 𝑪 𝔠 𝒞 𝘤 𝖢 𝙘 𐌂 𝗖 ꓚ 𝐜 Ꮯ 𝑐 𐔜 𝕔 ⲥ 𝓒 𝖈 𝚌 ℭ 𝘊 ꮯ ϲ 𝘾 𝒄 🝌 𝐂 𑣲 𝒸 𑣩 𐊢 𝐶 𝖼 ⅽ",
            "d # d ԁ 𝕯 𝓭 ⅅ 𝙳 ⅆ 𝗱 𝘥 𝑫 𝒟 ꓒ 𝐝 ꓓ 𝖣 𝔡 𝗗 𝕕 ᗞ 𝙙 Ꭰ 𝚍 𝓓 𝑑 Ꮷ 𝔇 ᗪ 𝒅 𝘋 𝖉 ᑯ 𝘿 𝖽 𝐃 𝐷 𝔻 ⅾ 𝒹"
        }, "𝒜𝑩ℂ𝘿",
                "abcd", "𝒜𝑩ℂ𝘿"
        );
        
        test(new String[]{
            "test t" // Invalid, since "test" is longer than "t"
        }, "t",
                (String[]) null
        );
        
        test(new String[]{
            "test t", // Invalid, since "test" is longer than "t"
            "a @"
        }, "th@t",
                "that", "th@t"
        );
        
        test(new String[]{
            "test t testi" // "test" is longer than "t", but "testi" is valid
        }, "testi",
                "test", "testi",
                "testi", null
        );
        
        test(new String[]{
            "test test1 test2",
            "abc abc1 abc2"
        }, "test1 abc2",
                "test", "test1",
                "abc", "abc2"
        );
        
        /**
         * Uppercase ſ would be S, so check that it successfully rejects the
         * character when creating uppercase characters for case-insensitivity.
         * It shouldn't change S to anything else.
         */
        test(new String[]{
            "f ſ"
        }, "S",
                "S", "S"
        );
        
        //--------------------------
        // Index original to changed
        //--------------------------
        test2(new String[]{
            "a 𝒜"
        }, "h𝒜t",
                "h𝒜t", "hat"
        );
        
        test2(new String[]{
            "a 𝒜"
        }, "h𝒜t c𝒜t",
                "h𝒜t", "hat",
                "cat", null,
                "c𝒜t", "cat"
        );
        
        test2(new String[]{
            "a # a 𝝰 𝙰 ａ 𝗮 𝐴 ᗅ 𝑨 𝚨 𝕬 𝓪 𝞪 𝖠 ɑ 𝔞 𝘢 𝛢 𝙖 𝝖 𝒜 𝜜 𝐚 𖽀 𝓐 𝞐 𝑎 𝗔 𝕒 𝘈 𝖆 Ꭺ 𝚊 ꓮ а 𝐀 α 𝔄 𝒂 𝛂 𝔸 ⍺ 𝒶 𝜶 𐊠 𝛼 𝘼 𝖺",
            "b # b 𝗯 ｂ 𝕭 ƅ 𝙱 ь 𝓫 Ꮟ 𝑩 𝚩 ꓐ 𝔟 𝜝 𝘣 𝛣 𝖡 𝙗 𝝗 𐊂 𐌁 𝗕 𝐛 𝑏 𝕓 𝓑 𝞑 𝖇 𝔅 ℬ 𝚋 ᖯ 𝘉 ᑲ β в 𝘽 Ꞵ Ᏼ 𝒃 𝐁 ᗷ 𝒷 𐊡 𝐵 𝖻 𝔹",
            "c # c 𝗰 с ℂ 𝕮 ｃ ᴄ 𝙲 𐐽 𝓬 𝑪 𝔠 𝒞 𝘤 𝖢 𝙘 𐌂 𝗖 ꓚ 𝐜 Ꮯ 𝑐 𐔜 𝕔 ⲥ 𝓒 𝖈 𝚌 ℭ 𝘊 ꮯ ϲ 𝘾 𝒄 🝌 𝐂 𑣲 𝒸 𑣩 𐊢 𝐶 𝖼 ⅽ",
            "d # d ԁ 𝕯 𝓭 ⅅ 𝙳 ⅆ 𝗱 𝘥 𝑫 𝒟 ꓒ 𝐝 ꓓ 𝖣 𝔡 𝗗 𝕕 ᗞ 𝙙 Ꭰ 𝚍 𝓓 𝑑 Ꮷ 𝔇 ᗪ 𝒅 𝘋 𝖉 ᑯ 𝘿 𝖽 𝐃 𝐷 𝔻 ⅾ 𝒹"
        }, "𝒜𝑩ℂ𝘿",
                "𝘿", "d",
                "𝒜𝑩ℂ𝘿", "abcd"
        );
    }
    
    private static void test(String[] items, String message, String... searchAndExpected) {
        test(items, message, false, searchAndExpected);
    }
    
    private static void test2(String[] items, String message, String... searchAndExpected) {
        test(items, message, true, searchAndExpected);
    }
    
    private static void test(String[] items, String message, boolean reverse, String... searchAndExpected) {
        Replacer2.Result result = Replacer2.create(Arrays.asList(items)).replace(message);
        if (searchAndExpected == null) {
            assertNull(result);
            return;
        }
        for (int i = 0; i < searchAndExpected.length; i += 2) {
            String search = searchAndExpected[i];
            String expected = searchAndExpected[i+1];
            Matcher m = Pattern.compile(search).matcher(reverse ? message : result.getChangedText());
            if (m.find()) {
                int start = reverse ? result.indexToChanged(m.start()) : result.indexToOriginal(m.start());
                int end = reverse ? result.indexToChanged(m.end()) : result.indexToOriginal(m.end());
                String actual = reverse ? result.getChangedText().substring(start, end) : message.substring(start, end); 
                assertEquals(expected, actual);
            }
            else {
                assertNull(expected);
            }
        }
    }
    
}
