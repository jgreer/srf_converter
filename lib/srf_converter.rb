
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
    
    
  end
end
