package rs117.hd.spikes.vulkan.control;

@FunctionalInterface
interface VulkanFailureInjector
{
	VulkanFailureInjector NONE = point -> {};

	void check(String point);
}
