package gearbox;

import javax.media.j3d.*;
import javax.vecmath.*;

// generate a full or partial cylinder with or without endcaps
public class Shaft extends TransformGroup {
    double radius;
    double length;
    int segmentCount;
    boolean mapped;
    boolean normalSign;

    public Shaft(double radius, double length, int segmentCount, Appearance bodyLook,
                 Appearance endLook, boolean mapped, double arc, double offset,
                 boolean normalSign) {
        super();
        this.radius = radius;
        this.length = length;
        this.segmentCount = segmentCount;
        this.mapped = mapped;
        this.normalSign = normalSign;
        section(bodyLook, endLook, arc, offset);
    }

    // generate segments of cylinder with different appearances
    public void section(Appearance bodyLook, Appearance endLook, double arc, double offset) {
        int flags;
        double frontZ = -0.5 * length;
        double rearZ = 0.5 * length;

        // surface normal for lighting
        Vector3f normal = new Vector3f(1.0F, 0.0F, 0.0F);
        Point3d coordinate1 = new Point3d(0.0, 0.0, 0.0);
        Point3d coordinate2 = new Point3d(0.0, 0.0, 0.0);
        double segmentAngle = arc / segmentCount;
        this.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
        if (mapped) {
            flags = (GeometryArray.NORMALS | GeometryArray.COORDINATES |
                     GeometryArray.TEXTURE_COORDINATE_2);
        } else {
            flags = (GeometryArray.NORMALS | GeometryArray.COORDINATES);
        }

        // construct front & rear end faces
        if (endLook != null) {
            this.addChild(new Disk(frontZ, -1, radius, segmentCount, arc, offset, endLook));
            this.addChild(new Disk(rearZ, 1, radius, segmentCount, arc, offset, endLook));
        }

        // construct shaft's outer skin (the cylinder body)
        int shaftVertexCount = 2 * segmentCount + 2;
        int[] shaftStripCount = {shaftVertexCount,};
        int index;
        double tempAngle, xDirection, yDirection, xShaft, yShaft;
        float mapping;
        TriangleStripArray triangles = new TriangleStripArray(shaftVertexCount, flags,
                shaftStripCount);
        for (int count = 0; count < segmentCount + 1; count++) {
            index = count * 2;
            tempAngle = segmentAngle * count + offset;
            xDirection = Math.cos(tempAngle);
            yDirection = Math.sin(tempAngle);
            xShaft = radius * xDirection;
            yShaft = radius * yDirection;
            normal.set((float) xDirection, (float) yDirection, 0.0F);
            if (normalSign) {
                normal.negate();
                coordinate1.set(xShaft, yShaft, frontZ);
                coordinate2.set(xShaft, yShaft, rearZ);
            } else {
                coordinate1.set(xShaft, yShaft, rearZ);
                coordinate2.set(xShaft, yShaft, frontZ);
            }
            normal.normalize();

            triangles.setCoordinate(index, coordinate1);
            mapping = (float) count / (float) segmentCount - 1;
            if (mapped) {
                triangles.setTextureCoordinate(0, index, new TexCoord2f(mapping, 0.0F));
            }
            triangles.setNormal(index, normal);

            triangles.setCoordinate(index + 1, coordinate2);
            if (mapped) {
                triangles.setTextureCoordinate(0, index + 1, new TexCoord2f(mapping, 1.0F));
            }
            triangles.setNormal(index + 1, normal);
        }
        this.addChild(new Shape3D(triangles, bodyLook));
    }
}
