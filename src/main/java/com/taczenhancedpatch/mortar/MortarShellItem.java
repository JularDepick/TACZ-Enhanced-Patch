package com.taczenhancedpatch.mortar;

import com.taczenhancedpatch.shell.ShellItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;

/*
 * 迫击炮炮弹。
 * 属性与手雷同一套, 随物品数据走; 不能手持投掷, 只能装进迫击炮。
 */
public class MortarShellItem extends ShellItem {
    public MortarShellItem(Properties properties) {
        super(properties);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }
}
