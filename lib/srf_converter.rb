
class VehicleImage
  attr_accessor :images_3d, :images_2d
  def initialize(path)
    puts "Loading vehicle: #{path}"
    if path =~ /\.srf$/i
      @full_image = load_srf(path)
    else
      @full_image = javax.imageio.ImageIO.read(java.io.File.new(path))
    end
    if @full_image.height >= 300
      @animated = true
      raise "Don't yet support animated ones..."
    else
      @animated = false
      @size_3d = (@full_image.width / 360.0).floor * 10
      @size_2d = ((@full_image.height - @size_3d) / 10.0).floor * 10
    end
    puts "  Animated: #{@animated ? 'Yes' : 'No'}"
    puts "  3D size: #{@size_3d}"
    puts "  2D size: #{@size_2d}"

    @images_3d = (0..35).to_a.collect { |x| @full_image.get_subimage(x*@size_3d, 0, @size_3d, @size_3d) }
    @images_2d = (0..35).to_a.collect { |x| @full_image.get_subimage(x*@size_2d, @size_3d, @size_2d, @size_2d) }
    
  end
  def load_srf(path)
    raise "Can't yet load SRF images."
  end
end

class SrfConverter
  def initialize(view_builder)
    @view_builder = view_builder
    
    @loaded_file = nil

    @view_builder.set_callback :open, Proc.new { load_image(@view_builder.get_file_to_open) }
    @view_builder.set_callback :exit, Proc.new { java.lang.System.exit(0) }
  end

  def run
    main_frame = @view_builder.build
    main_frame.visible = true
  end
  
  def load_image(image_file)
    return if image_file.nil?
    @vehicle = VehicleImage.new(image_file)
    @view_builder.animation_panel.set_images(@vehicle.images_3d)
  end
end
