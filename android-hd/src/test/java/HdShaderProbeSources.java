import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import rs117.hd.AndroidSupport;
import rs117.hd.opengl.shader.ShaderIncludes;
import rs117.hd.utils.ResourcePath;

/** Driver preflight using upstream include expansion and the same Android shader adaptation. */
public class HdShaderProbeSources {
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]), out=Path.of(args[1]); Files.createDirectories(out);
        Map<String,String> defs=new HashMap<>();
        try(var paths=Files.walk(root)) {
            for(Path p:(Iterable<Path>)paths.filter(p->p.toString().endsWith(".glsl"))::iterator) {
                Matcher m=Pattern.compile("(?m)^\\s*#include ([A-Z_]+)\\s*$").matcher(Files.readString(p));
                while(m.find()) defs.put(m.group(1),"0");
            }
        }
        // Fixture counts exercise indexed UBO arrays; actual game counts are verified during live startup.
        defs.put("ZONE_RENDERER","1"); defs.put("MATERIAL_COUNT","512"); defs.put("WATER_TYPE_COUNT","32");
        defs.put("MAX_SIMULTANEOUS_WORLD_VIEWS","64"); defs.put("MAX_LIGHT_COUNT","16");
        defs.put("TILED_LIGHTING_LAYER_COUNT","1"); defs.put("TILED_LIGHTING_TILE_SIZE","16");
        defs.put("SHADOW_RESOLUTION","1024"); defs.put("NORMAL_MAPPING","1");
        defs.put("UNDO_VANILLA_SHADING","1"); defs.put("WIND_DISPLACEMENT_NOISE_RESOLUTION","0.04");
        StringBuilder materials=new StringBuilder(); Set<String> names=new TreeSet<>();
        try(var paths=Files.walk(root)) {
            for(Path p:(Iterable<Path>)paths.filter(p->p.toString().endsWith(".glsl"))::iterator) {
                Matcher m=Pattern.compile("\\bMAT_[A-Z_0-9]+\\b").matcher(Files.readString(p));
                while(m.find()) names.add(m.group());
            }
        }
        int mi=0; for(String name:names) materials.append("#define ").append(name).append(" getMaterial(").append(mi++).append(")\n");
        for(String mode:List.of("basic","lit","shadow","mobile","tiled","effects")) {
            for(String name:List.of("scene_vert.glsl","scene_frag.glsl","ui_vert.glsl","ui_frag.glsl","shadow_vert.glsl","shadow_frag.glsl",
                "overlays/overlay_vert.glsl", "overlays/gamma_calibration_frag.glsl")) {
                ShaderIncludes inc=new ShaderIncludes().addIncludePath(ResourcePath.path(root.toString()));
                defs.forEach(inc::define);
                inc.define("SHADOW_MODE",mode.equals("shadow")?2:0)
                   .define("DYNAMIC_LIGHTS",!mode.equals("basic"))
                   .define("PARALLAX_OCCLUSION_MAPPING",mode.equals("shadow"))
                   .define("SHADOW_TRANSPARENCY",mode.equals("shadow"))
                   .define("UI_SCALING_MODE", List.of("basic","lit","shadow","mobile","tiled","effects").indexOf(mode))
                   .define("WIND_DISPLACEMENT", mode.equals("mobile") || mode.equals("effects"))
                   .define("CHARACTER_DISPLACEMENT", mode.equals("effects"))
                   .define("MAX_CHARACTER_POSITION_COUNT", 16)
                   .define("TILED_LIGHTING", mode.equals("tiled"))
                   .define("FLAT_SHADING", mode.equals("effects"))
                   .define("APPLY_COLOR_FILTER", mode.equals("effects"))
                   .define("COLOR_BLINDNESS", mode.equals("effects") ? 1 : 0)
                   .define("WIREFRAME", mode.equals("effects"))
                   .addInclude("MATERIAL_GETTER","#define getMaterial(i) MaterialArray[i]")
                   .addInclude("WATER_TYPE_GETTER","#define getWaterType(i) WaterTypeArray[i]")
                   .addInclude("WORLD_VIEW_GETTER","#define getWorldView(i) WorldViewArray[i]")
                   .addInclude("MATERIAL_CONSTANTS",materials.toString())
                   .addInclude("SHADER_TYPE","#define VERTEX_SHADER 35633\n#define FRAGMENT_SHADER 35632\n#define GEOMETRY_SHADER 36313\n#define COMPUTE_SHADER 37305\n#define SHADER_TYPE "+(name.contains("vert")?35633:35632));
                String outputName=name.replace("overlays/overlay", "gamma").replace("overlays/gamma_calibration", "gamma");
                Path file=out.resolve(mode+"_"+outputName);
                if(mode.equals("mobile")) inc.define("UI_SCALING_MODE", 5).define("PARALLAX_OCCLUSION_MAPPING", true).define("DYNAMIC_LIGHTS", false);
                Files.writeString(file,AndroidSupport.adaptShader(inc.loadFile(name)));
                var sources=ShaderIncludes.class.getDeclaredField("includeList"); sources.setAccessible(true);
                @SuppressWarnings("unchecked") var paths=(List<String>)sources.get(inc);
                Files.write(out.resolve(mode+"_"+outputName+".sources"),paths);
                System.out.println(file.getFileName());
            }
        }
    }
}
