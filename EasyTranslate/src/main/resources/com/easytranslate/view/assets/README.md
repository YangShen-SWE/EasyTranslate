# The Cat, Plant, and Coffee Cup

These images were generated for this project with OpenAI ImageGen while I was using GPT to help design the UI. I kept the simple cat with a plant and coffee cup beside it, rather than adding a whole keyboard and mouse setup.

- `cat.png`: the outline cat resting on the top edge.
- `desk.png`: the mint green plant and coffee cup.

The outside is transparent and the artwork has dark gray interiors; otherwise the white outlines are hard to see over a white app. `FloatingViewController` uses an ImageView viewport to remove the outer padding without cropping the source file.

`CompanionMotion` now uses the same two images, separating the paws and steam for animation. The head, plant, and cup stay still. Turning effects off restores the resting pose.

Button icons come from the [original Lucide SVGs](https://github.com/lucide-icons/lucide/tree/main/icons), retrieved on 2026-09-28. They were not redrawn. The ISC and inherited MIT notices are kept in [LICENSE.txt](../icons/LICENSE.txt). If the icons change later, their sources and licenses need to stay with them too.

---

# 小猫、花盆和咖啡杯

这几张图是这次用 GPT 设计 UI 时，通过 OpenAI ImageGen 为项目生成的。最后还是保留了简洁的小猫，旁边放花盆和咖啡杯，没有再塞一整套键盘鼠标进去。

- `cat.png`：趴在窗口上边的线条小猫。
- `desk.png`：薄荷绿植物和咖啡杯。

图片外部透明，图案内部是深灰色，不然放到白色应用上面时白线容易看不清。`FloatingViewController` 用 ImageView 视口去掉图片外围留白，源文件本身不用裁掉。

现在 `CompanionMotion` 会继续用这两张图，把左右爪和热气分开做动画。猫头、花盆和杯身保持不动，关掉动效就回到静止的样子。

按钮图标来自 [Lucide 原始 SVG](https://github.com/lucide-icons/lucide/tree/main/icons)，取用日期是 2026-09-28。图标没有改绘，ISC 和继承的 MIT 许可说明保留在 [LICENSE.txt](../icons/LICENSE.txt)，后面换图标也要一起保留对应来源和许可。
