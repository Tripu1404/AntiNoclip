private boolean isInsideSolidBlock(Player player, AxisAlignedBB playerBox) {
    int minX = (int) Math.floor(playerBox.getMinX());
    int maxX = (int) Math.floor(playerBox.getMaxX());
    int minY = (int) Math.floor(playerBox.getMinY());
    int maxY = (int) Math.floor(playerBox.getMaxY());
    int minZ = (int) Math.floor(playerBox.getMinZ());
    int maxZ = (int) Math.floor(playerBox.getMaxZ());

    for (int x = minX; x <= maxX; x++) {
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                Block block = player.getLevel().getBlock(x, y, z);
                
                // 1. Filtros básicos rápidos
                if (block instanceof BlockAir || block.getBoundingBox() == null) continue;
                if (block instanceof BlockChest) continue;

                String name = block.getName().toLowerCase();

                // 2. EXCEPCIONES: Ignorar bloques donde el jugador PUEDE estar dentro
                // Añadimos shulker, item_frame, y mantenemos arena/grava
                if (name.contains("shulker") || 
                    name.contains("frame") || 
                    name.contains("sand") || 
                    name.contains("gravel") ||
                    name.contains("slab") || // Las losas suelen dar falsos positivos
                    name.contains("stair")) { // Las escaleras también
                    continue;
                }

                AxisAlignedBB blockBox = block.getBoundingBox();

                // 3. Verificación de intersección real
                if (blockBox.shrink(0.1, 0.1, 0.1).intersectsWith(playerBox)) {
                    double overlapY = Math.min(blockBox.getMaxY(), playerBox.getMaxY()) - Math.max(blockBox.getMinY(), playerBox.getMinY());
                    double overlapX = Math.min(blockBox.getMaxX(), playerBox.getMaxX()) - Math.max(blockBox.getMinX(), playerBox.getMinX());
                    double overlapZ = Math.min(blockBox.getMaxZ(), playerBox.getMaxZ()) - Math.max(blockBox.getMinZ(), playerBox.getMinZ());

                    // Aumentamos ligeramente el margen a 0.4 para evitar falsos positivos por lag
                    if (overlapX > 0.4 && overlapY > 0.4 && overlapZ > 0.4) {
                        // El Ender Chest se maneja aquí específicamente si quieres bloquearlo
                        if (block instanceof BlockEnderChest) return true;
                        
                        // Si el bloque es sólido y no está en la lista de excepciones, es un posible glitch
                        return true;
                    }
                }
            }
        }
    }
    return false;
}
