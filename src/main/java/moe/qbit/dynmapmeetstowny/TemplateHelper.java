package moe.qbit.dynmapmeetstowny;

import com.github.mustachejava.Mustache;
import com.google.common.collect.Maps;

import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class TemplateHelper {
    public static Map<String, Object> createScope(){
        HashMap<String,Object> ret = Maps.newHashMap();
        ret.put("trim",(Function<String,String>)String::trim);
        ret.put("pop",(Function<String,String>)TemplateHelper::pop);
        ret.put("pop2",(Function<String,String>)TemplateHelper::pop2);
        return ret;
    }

    public static String render(final Mustache template, final Map<String,?> values) {
        final Writer out = new StringWriter();
        template.execute(out, values);
        return out.toString();
    }

    public static String pop(String in){return in.substring(0,in.length()-1);}
    public static String pop2(String in){return in.substring(0,in.length()-2);}
}
