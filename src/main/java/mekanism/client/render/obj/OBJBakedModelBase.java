package mekanism.client.render.obj;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.Mekanism;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.client.model.obj.OBJModel;
import net.minecraftforge.client.model.obj.OBJModel.*;
import net.minecraftforge.client.model.pipeline.LightUtil;
import net.minecraftforge.client.model.pipeline.UnpackedBakedQuad;
import net.minecraftforge.common.model.IModelState;
import net.minecraftforge.common.model.TRSRTransformation;

import javax.annotation.Nonnull;
import javax.vecmath.Matrix4f;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

public abstract class OBJBakedModelBase extends OBJBakedModel {

    private static Method m_updateStateVisibilityMap;
    private static Field f_textures;
    protected IBakedModel baseModel;
    protected TextureAtlasSprite tempSprite = ModelLoader.White.INSTANCE;
    protected VertexFormat vertexFormat;
    protected ImmutableMap<String, TextureAtlasSprite> textureMap;
    protected Map<TransformType, Matrix4f> transformationMap;

    static {
        try {
            f_textures = OBJBakedModel.class.getDeclaredField("textures");
            f_textures.setAccessible(true);
            m_updateStateVisibilityMap = OBJBakedModel.class.getDeclaredMethod("updateStateVisibilityMap", OBJState.class);
            m_updateStateVisibilityMap.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Could not set up reflection helpers", e);
        }
    }

    public OBJBakedModelBase(IBakedModel base, OBJModel model, IModelState state, VertexFormat format, ImmutableMap<String, TextureAtlasSprite> textures,
                             Map<TransformType, Matrix4f> transform) {
        model.super(model, state, format, textures);
        baseModel = base;
        transformationMap = transform;
        textureMap = textures;
        vertexFormat = format;

        if (state instanceof OBJState) {
            updateStateVisibilityMap((OBJState) state);
        }
    }

    public static void putVertexData(UnpackedBakedQuad.Builder builder, Vertex v, Normal faceNormal, TextureCoordinate defUV, TextureAtlasSprite sprite, VertexFormat format, float[] color) {
        for (int e = 0; e < format.getElementCount(); e++) {
            switch (format.getElement(e).getUsage()) {
                case POSITION:
                    builder.put(e, v.getPos().x, v.getPos().y, v.getPos().z, v.getPos().w);
                    break;
                case COLOR:
                    float d;
                    if (v.hasNormal()) {
                        d = LightUtil.diffuseLight(v.getNormal().x, v.getNormal().y, v.getNormal().z);
                    } else {
                        d = LightUtil.diffuseLight(faceNormal.x, faceNormal.y, faceNormal.z);
                    }
                    builder.put(e, d * v.getMaterial().getColor().x * color[0], d * v.getMaterial().getColor().y * color[1], d * v.getMaterial().getColor().z * color[2],
                            v.getMaterial().getColor().w * color[3]);

                    break;
                case UV:
                    if (!v.hasTextureCoordinate()) {
                        builder.put(e, sprite.getInterpolatedU(defUV.u * 16), sprite.getInterpolatedV((1 - defUV.v) * 16), 0, 1);
                    } else {
                        builder.put(e, sprite.getInterpolatedU(v.getTextureCoordinate().u * 16), sprite.getInterpolatedV((1 - v.getTextureCoordinate().v) * 16), 0, 1);
                    }
                    break;
                case NORMAL:
                    if (!v.hasNormal()) {
                        builder.put(e, faceNormal.x, faceNormal.y, faceNormal.z, 0);
                    } else {
                        builder.put(e, v.getNormal().x, v.getNormal().y, v.getNormal().z, 0);
                    }
                    break;
                default:
                    builder.put(e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static ImmutableMap<String, TextureAtlasSprite> getTexturesForOBJModel(IBakedModel model) {
        try {
            return (ImmutableMap<String, TextureAtlasSprite>) f_textures.get(model);
        } catch (ReflectiveOperationException | ClassCastException e) {
            Mekanism.logger.error("Could not get private field textures", e);
        }
        return ImmutableMap.of();
    }

    @Nonnull
    @Override
    public List<BakedQuad> getQuads(IBlockState blockState, EnumFacing side, long rand) {
        if (side != null) {
            return ImmutableList.of();
        }

        List<BakedQuad> bakedQuads = new ArrayList<>();

        Set<Face> faces = Collections.synchronizedSet(new LinkedHashSet<>());
        Optional<TRSRTransformation> transform = Optional.empty();
        Map<Face, String> groupNameMap = new HashMap<>();

        for (Group g : getModel().getMatLib().getGroups().values()) {
            if (getState() instanceof OBJState) {
                OBJState state = (OBJState) getState();

                if (state.parent != null) {
                    transform = state.parent.apply(Optional.empty());
                }

                updateStateVisibilityMap(state);

                if (state.getGroupsWithVisibility(true).contains(g.getName())) {
                    Set<Face> groupFaces = g.applyTransform(transform);
                    for (Face f : groupFaces) {
                        groupNameMap.put(f, g.getName());
                        faces.add(f);
                    }
                }
            } else {
                transform = getState().apply(Optional.empty());
                Set<Face> groupFaces = g.applyTransform(transform);

                for (Face f : groupFaces) {
                    groupNameMap.put(f, g.getName());
                    faces.add(f);
                }
            }
        }

        for (Face f : faces) {
            String groupName = groupNameMap.get(f);

            TextureAtlasSprite overrideTexture = getOverrideTexture(f, groupName);
            if (overrideTexture != null) {
                tempSprite = overrideTexture;
            } else if (getModel().getMatLib().getMaterial(f.getMaterialName()).isWhite()) {
                for (Vertex v : f.getVertices()) {
                    if (!v.getMaterial().equals(getModel().getMatLib().getMaterial(v.getMaterial().getName()))) {
                        v.setMaterial(getModel().getMatLib().getMaterial(v.getMaterial().getName()));
                    }
                }
                tempSprite = ModelLoader.White.INSTANCE;
            } else {
                tempSprite = textureMap.get(f.getMaterialName());
            }
            float[] color = new float[]{1, 1, 1, 1};

            float[] overrideColor = getOverrideColor(f, groupName);
            if (overrideColor != null) {
                color = overrideColor;
            }

            EnumFacing facing = EnumFacing.getFacingFromVector(f.getNormal().x, f.getNormal().y, f.getNormal().z);
            UnpackedBakedQuad.Builder builder = new UnpackedBakedQuad.Builder(vertexFormat);
            builder.setContractUVs(true);
            builder.setQuadOrientation(facing);
            builder.setTexture(tempSprite);
            builder.setQuadTint(0);

            Normal faceNormal = f.getNormal();
            boolean rotate = shouldRotate(f, groupName);

            putVertexData(builder, f.getVertices()[0], faceNormal, TextureCoordinate.getDefaultUVs()[0], tempSprite, vertexFormat, color);
            putVertexData(builder, f.getVertices()[1], faceNormal, TextureCoordinate.getDefaultUVs()[1], tempSprite, vertexFormat, color);
            putVertexData(builder, f.getVertices()[2], faceNormal, TextureCoordinate.getDefaultUVs()[2], tempSprite, vertexFormat, color);
            putVertexData(builder, f.getVertices()[3], faceNormal, TextureCoordinate.getDefaultUVs()[3], tempSprite, vertexFormat, color);

            BakedQuad quad = builder.build();
            if (rotate) {
                quad = MekanismRenderer.rotate(quad, 1);
            }
            bakedQuads.add(quad);
        }
        return bakedQuads;
    }

    @SuppressWarnings("deprecation")
    protected void updateStateVisibilityMap(OBJState state) {
        try {
            m_updateStateVisibilityMap.invoke(this, state);
        } catch (ReflectiveOperationException | ClassCastException e) {
            Mekanism.logger.error("Could not get private field updateStateVisibilityMap", e);
        }
    }

    @Nonnull
    @Override
    public TextureAtlasSprite getParticleTexture() {
        return tempSprite;
    }

    protected float[] getOverrideColor(Face f, String groupName) {
        return null;
    }

    protected TextureAtlasSprite getOverrideTexture(Face f, String groupName) {
        return null;
    }

    protected boolean shouldRotate(Face f, String groupName) {
        return false;
    }
}
